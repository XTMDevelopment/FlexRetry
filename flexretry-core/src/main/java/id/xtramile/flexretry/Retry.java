package id.xtramile.flexretry;

import id.xtramile.flexretry.config.RetryConfig;
import id.xtramile.flexretry.config.RetryTemplate;
import id.xtramile.flexretry.lifecycle.AttemptLifecycle;
import id.xtramile.flexretry.strategy.backoff.BackoffRouter;
import id.xtramile.flexretry.strategy.backoff.BackoffStrategy;
import id.xtramile.flexretry.strategy.backoff.FixedBackoff;
import id.xtramile.flexretry.strategy.policy.*;
import id.xtramile.flexretry.strategy.stop.FixedAttemptsStop;
import id.xtramile.flexretry.strategy.stop.StopStrategy;
import id.xtramile.flexretry.strategy.timeout.AttemptTimeoutStrategy;
import id.xtramile.flexretry.support.time.Clock;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.*;

/**
 * Public facade with a fluent Builder
 */
public final class Retry<T> {
    private Retry() {
    }

    public static <T> Builder<T> newBuilder() {
        return new Builder<>();
    }

    public static <T> RetryTemplate<T> template(RetryConfig<T> retryConfig) {
        return new RetryTemplate<>(retryConfig);
    }

    public static final class Builder<T> {

        // ---------- Identity / tags ----------
        private String name = "retry";
        private String id = UUID.randomUUID().toString();
        private final Map<String, Object> tags = new HashMap<>();

        // ---------- Stop / timing ----------
        private StopStrategy stop = new FixedAttemptsStop(3);
        private BackoffStrategy backoff = new FixedBackoff(Duration.ZERO);
        private BackoffRouter backoffRouter = null;
        private AttemptTimeoutStrategy attemptTimeouts = null;
        private Duration attemptTimeout = null;
        private ExecutorService attemptExecutor = null;

        // ---------- Policies ----------
        private final List<RetryPolicy<T>> policies = new ArrayList<>();

        // ---------- Infra ----------
        private final RetryListeners<T> listeners = new RetryListeners<>();
        private Sleeper sleeper = Sleeper.system();
        private Clock clock = Clock.system();
        private AttemptLifecycle<T> lifecycle = null;

        // ---------- Task / fallback ----------
        private Callable<T> task;
        private Function<Throwable, T> fallback = null;

        // ======== Fluent configuration ========

        // Identity & tags
        public Builder<T> name(String name) {
            this.name = Objects.requireNonNull(name);
            return this;
        }

        public Builder<T> id(String id) {
            this.id = Objects.requireNonNull(id);
            return this;
        }

        public Builder<T> tag(String key, Object value) {
            tags.put(key, value);
            return this;
        }

        // Stop timing
        public Builder<T> maxAttempts(int attempts) {
            this.stop = new FixedAttemptsStop(attempts);
            return this;
        }

        public Builder<T> stop(StopStrategy stop) {
            this.stop = Objects.requireNonNull(stop);
            return null;
        }

        public Builder<T> delayMillis(long millis) {
            this.backoff = new FixedBackoff(Duration.ofMillis(Math.max(0L, millis)));
            return this;
        }

        public Builder<T> backoff(BackoffStrategy backoff) {
            this.backoff = Objects.requireNonNull(backoff, "backoff");
            return this;
        }

        public Builder<T> backoffRouter(BackoffRouter backoffRouter) {
            this.backoffRouter = backoffRouter;
            return this;
        }

        public Builder<T> attemptTimeouts(AttemptTimeoutStrategy strategy) {
            this.attemptTimeouts = strategy;
            return this;
        }

        public Builder<T> attemptTimeout(Duration duration) {
            this.attemptTimeout = duration;
            return this;
        }

        public Builder<T> attemptExecutor(ExecutorService executor) {
            this.attemptExecutor = executor;
            return this;
        }

        // Policies (compose via OR by default)
        public Builder<T> retryIf(Predicate<T> predicate) {
            this.policies.add(new ResultPredicateRetryPolicy<>(Objects.requireNonNull(predicate, "predicate")));
            return this;
        }

        public Builder<T> classify(ClassifierPolicy.ResultClassifier<T> classifier) {
            this.policies.add(new ClassifierPolicy<>(classifier));
            return this;
        }

        public Builder<T> policy(RetryPolicy<T> policy) {
            this.policies.add(Objects.requireNonNull(policy, "policy"));
            return this;
        }

        public Builder<T> retryOnlyWhen(RetryWindow window) {
            this.policies.add(new WindowPolicy<>(window, this.clock));
            return this;
        }

        @SafeVarargs
        public final Builder<T> retryOn(Class<? extends Throwable>... errors) {
            this.policies.add(new ExceptionRetryPolicy<>(errors));
            return this;
        }

        // Hooks / listeners
        public Builder<T> onAttempt(Consumer<RetryContext<T>> consumer) {
            listeners.onAttempt(consumer);
            return this;
        }

        public Builder<T> afterAttemptSuccess(BiConsumer<T, RetryContext<T>> consumer) {
            listeners.afterAttemptSuccess = consumer;
            return this;
        }

        public Builder<T> afterAttemptFailure(BiConsumer<Throwable, RetryContext<T>> consumer) {
            listeners.afterAttemptFailure = consumer;
            return this;
        }

        public Builder<T> onSuccess(BiConsumer<T, RetryContext<T>> consumer) {
            listeners.onSuccess(consumer);
            return this;
        }

        public Builder<T> onSuccess(Consumer<T> consumer) {
            return onSuccess((result, ctx) -> consumer.accept(result));
        }

        public Builder<T> onFailure(BiConsumer<Throwable, RetryContext<T>> consumer) {
            listeners.onFailure(consumer);
            return this;
        }

        public Builder<T> onFailure(Consumer<Throwable> consumer) {
            return onFailure((exception, ctx) -> consumer.accept(exception));
        }

        public Builder<T> onFinally(Consumer<RetryContext<T>> consumer) {
            listeners.onFinally(consumer);
            return this;
        }

        public Builder<T> beforeSleep(BiFunction<Duration, RetryContext<T>, Duration> function) {
            listeners.beforeSleep = function;
            return this;
        }

        public Builder<T> lifecycle(AttemptLifecycle<T> lifecycle) {
            this.lifecycle = lifecycle;
            return this;
        }

        // Infra injection
        Builder<T> sleeper(Sleeper sleeper) {
            this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
            return this;
        }

        Builder<T> clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        // Fallback
        public Builder<T> fallback(Function<Throwable, T> fallback) {
            this.fallback = fallback;
            return this;
        }

        // Task
        public Builder<T> execute(Supplier<T> supplier) {
            Objects.requireNonNull(supplier, "supplier");
            this.task = supplier::get;
            return this;
        }

        public Builder<T> execute(Callable<T> callable) {
            this.task = Objects.requireNonNull(callable, "callable");
            return this;
        }

        // ======== Build & Run ========
        public RetryConfig<T> toConfig() {
            return new RetryConfig<>(
                    name, id, Map.copyOf(tags),
                    stop, backoff, buildPolicy(),
                    listeners, sleeper, clock,
                    attemptTimeout, attemptExecutor,
                    fallback,
                    backoffRouter,
                    lifecycle,
                    attemptTimeouts
            );
        }

        public T getResult() {
            return buildExecutor().run();
        }

        public CompletableFuture<T> getResultAsync(Executor executor) {
            RetryExecutor<T> exec = buildExecutor();
            return CompletableFuture.supplyAsync(exec::run, executor);
        }

        public RetryOutcome<T> getOutcome() {
            try {
                T result = getResult();
                return new RetryOutcome<>(true, result, null, 0);
            } catch (RetryException e) {
                return new RetryOutcome<>(false, null, e.getCause(), e.attempts());
            }
        }

        // ======== Internals ========
        private RetryExecutor<T> buildExecutor() {
            if (task == null) {
                throw new IllegalStateException("No task provided. Call execute(...) first.");
            }

            return new RetryExecutor<>(
                    name, id, Map.copyOf(tags),
                    stop, backoff,
                    buildPolicy(),
                    listeners, sleeper, clock,
                    attemptTimeout, attemptExecutor,
                    task, fallback,
                    backoffRouter,
                    lifecycle,
                    attemptTimeouts
            );
        }

        public RetryPolicy<T> buildPolicy() {
            if (policies.isEmpty()) {
                return (result, error, attempt, maxAttempts) -> false;
            }

            return Policies.or(policies.toArray(new RetryPolicy[0]));
        }
    }
}
