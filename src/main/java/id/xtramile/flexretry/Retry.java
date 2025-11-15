package id.xtramile.flexretry;

import id.xtramile.flexretry.backoff.BackoffRouter;
import id.xtramile.flexretry.backoff.BackoffStrategy;
import id.xtramile.flexretry.budget.RetryBudget;
import id.xtramile.flexretry.config.RetryConfig;
import id.xtramile.flexretry.config.RetryTemplate;
import id.xtramile.flexretry.http.RetryAfterExtractor;
import id.xtramile.flexretry.metrics.RetryMetrics;
import id.xtramile.flexretry.policy.*;
import id.xtramile.flexretry.stop.FixedAttemptsStop;
import id.xtramile.flexretry.stop.StopStrategy;
import id.xtramile.flexretry.time.Clock;
import id.xtramile.flexretry.tuning.MutableTuning;
import id.xtramile.flexretry.tuning.RetrySwitch;

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
    private Retry() {}

    public static <T> Builder<T> newBuilder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        // Identity
        private String name = "retry";
        private String id = UUID.randomUUID().toString();
        private final Map<String, Object> tags = new HashMap<>();

        // Timing & stop conditions
        private StopStrategy stop = new FixedAttemptsStop(3);
        private BackoffStrategy backoff = BackoffStrategy.fixed(Duration.ZERO);
        private Duration attemptTimeout = null;
        private ExecutorService attemptExecutor = null;

        // Policy
        private final List<RetryPolicy<T>> policies = new ArrayList<>();

        // Infra
        private final RetryListeners<T> listeners = new RetryListeners<>();
        private Sleeper sleeper = Sleeper.system();
        private Clock clock = Clock.system();
        private RetryBudget budget = RetryBudget.unlimited();
        private RetryMetrics metrics = RetryMetrics.noop();

        // Task / fallback
        private Callable<T> task;
        private Function<Throwable, T> fallback = null;

        private BackoffRouter backoffRouter = null;
        private RetryAfterExtractor<T> retryAfterExtractor = null;
        private RetrySwitch retrySwitch = null;
        private MutableTuning tuning = null;

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

        public Builder<T> maxAttempts(int attempts) {
            this.stop = new FixedAttemptsStop(attempts);
            return this;
        }

        public Builder<T> stop(StopStrategy stop) {
            this.stop = Objects.requireNonNull(stop);
            return null;
        }

        public Builder<T> delayMillis(long millis) {
            this.backoff = BackoffStrategy.fixed(Duration.ofMillis(Math.max(0L, millis)));
            return this;
        }

        public Builder<T> backoff(BackoffStrategy backoff) {
            this.backoff = Objects.requireNonNull(backoff, "backoff");
            return this;
        }

        @SafeVarargs
        public final Builder<T> retryOn(Class<? extends Throwable>... errors) {
            this.policies.add(new ExceptionRetryPolicy<>(errors));
            return this;
        }

        public Builder<T> retryIf(Predicate<T> predicate) {
            this.policies.add(new ResultPredicateRetryPolicy<>(Objects.requireNonNull(predicate, "predicate")));
            return this;
        }

        public Builder<T> classify(ClassifierPolicy.ResultClassifier<T> classifier) {
            this.policies.add(new ClassifierPolicy<>(classifier));
            return this;
        }

        public Builder<T> onAttempt(Consumer<RetryContext<T>> consumer) {
            listeners.onAttempt(consumer);
            return this;
        }

        public Builder<T> onSuccess(BiConsumer<T, RetryContext<T>> consumer) {
            listeners.onSuccess(consumer);
            return this;
        }

        public Builder<T> onFailure(BiConsumer<Throwable, RetryContext<T>> consumer) {
            listeners.onFailure(consumer);
            return this;
        }

        public Builder<T> onFinally(Consumer<RetryContext<T>> consumer) {
            listeners.onFinally(consumer);
            return this;
        }

        public Builder<T> beforeSleep(BiFunction<Duration, RetryContext<T>, Duration> function) {
            listeners.beforeSleep = function;
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

        public Builder<T> onRecover(Consumer<RetryContext<T>> consumer) {
            listeners.onRecover = consumer;
            return this;
        }

        public Builder<T> onSuccess(Consumer<T> consumer) {
            return onSuccess((result, ctx) -> consumer.accept(result));
        }

        public Builder<T> onFailure(Consumer<Throwable> consumer) {
            return onFailure((exception, ctx) -> consumer.accept(exception));
        }

        public Builder<T> attemptTimeout(Duration duration) {
            this.attemptTimeout = duration;
            return this;
        }

        public Builder<T> attemptExecutor(ExecutorService executor) {
            this.attemptExecutor = executor;
            return this;
        }

        Builder<T> sleeper(Sleeper sleeper) {
            this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
            return this;
        }

        Builder<T> clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        public Builder<T> budget(RetryBudget budget) {
            this.budget = Objects.requireNonNull(budget, "budget");
            return this;
        }

        public Builder<T> metrics(RetryMetrics metrics) {
            this.metrics = Objects.requireNonNull(metrics, "metrics");
            return this;
        }

        public Builder<T> fallback(Function<Throwable, T> fallback) {
            this.fallback = fallback;
            return this;
        }

        public Builder<T> backoffRouter(BackoffRouter backoffRouter) {
            this.backoffRouter = backoffRouter;
            return this;
        }

        public Builder<T> retryAfterExtractor(RetryAfterExtractor<T> retryAfterExtractor) {
            this.retryAfterExtractor = Objects.requireNonNull(retryAfterExtractor, "retryAfterExtractor");
            return this;
        }

        public Builder<T> globalSwitch(RetrySwitch retrySwitch) {
            this.retrySwitch = retrySwitch;
            return this;
        }

        public Builder<T> mutableTuning(MutableTuning tuning) {
            this.tuning = tuning;
            return this;
        }

        public Builder<T> execute(Supplier<T> supplier) {
            Objects.requireNonNull(supplier, "supplier");
            this.task = supplier::get;
            return this;
        }

        public Builder<T> execute(Callable<T> callable) {
            this.task = Objects.requireNonNull(callable, "callable");
            return this;
        }

        public RetryPolicy<T> buildPolicy() {
            if (policies.isEmpty()) {
                return (result, error, attempt, maxAttempts) -> false;
            }

            return Policies.or(policies.toArray(new RetryPolicy[0]));
        }

        public RetryConfig<T> toConfig() {
            return new RetryConfig<>(
                    name, id, Map.copyOf(tags),
                    stop, backoff, buildPolicy(), listeners,
                    sleeper, clock, budget, metrics,
                    attemptTimeout, attemptExecutor, fallback
            );
        }

        public static <T> RetryTemplate<T> template(RetryConfig<T> cfg) {
            return new RetryTemplate<>(cfg);
        }

        public T getResult() {
            return buildExecutor().run();
        }

        public CompletableFuture<T> getResultAsync(Executor executor) {
            RetryExecutor<T> exec = buildExecutor();
            return CompletableFuture.supplyAsync(exec::run, executor);
        }

        private RetryExecutor<T> buildExecutor() {
            if (task == null) {
                throw new IllegalStateException("No task provided. Call execute(...) first.");
            }

            return new RetryExecutor<>(
                    name, id, tags,
                    stop, backoff, buildPolicy(), listeners,
                    sleeper, clock, budget, metrics,
                    attemptTimeout, attemptExecutor,
                    task, fallback, backoffRouter, retryAfterExtractor
            );
        }
    }
}
