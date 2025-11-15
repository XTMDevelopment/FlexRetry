package id.xtramile.flexretry;

import id.xtramile.flexretry.backoff.BackoffRouter;
import id.xtramile.flexretry.backoff.BackoffStrategy;
import id.xtramile.flexretry.budget.RetryBudget;
import id.xtramile.flexretry.bulkhead.Bulkhead;
import id.xtramile.flexretry.cache.ResultCache;
import id.xtramile.flexretry.config.RetryConfig;
import id.xtramile.flexretry.config.RetryTemplate;
import id.xtramile.flexretry.events.RetryEventBus;
import id.xtramile.flexretry.health.HealthProbe;
import id.xtramile.flexretry.http.RetryAfterExtractor;
import id.xtramile.flexretry.lifecycle.AttemptLifecycle;
import id.xtramile.flexretry.metrics.RetryMetrics;
import id.xtramile.flexretry.policy.*;
import id.xtramile.flexretry.sf.SingleFlight;
import id.xtramile.flexretry.stop.FixedAttemptsStop;
import id.xtramile.flexretry.stop.StopStrategy;
import id.xtramile.flexretry.time.Clock;
import id.xtramile.flexretry.timeouts.AttemptTimeoutStrategy;
import id.xtramile.flexretry.trace.TraceContext;
import id.xtramile.flexretry.tuning.DynamicTuning;
import id.xtramile.flexretry.tuning.MutableTuning;
import id.xtramile.flexretry.tuning.RetrySwitch;
import id.xtramile.flexretry.window.RetryWindow;

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
        private BackoffStrategy backoff = BackoffStrategy.fixed(Duration.ZERO);
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
        private RetryBudget budget = RetryBudget.unlimited();
        private RetryMetrics metrics = RetryMetrics.noop();
        private RetryAfterExtractor<T> retryAfterExtractor = null;
        private RetrySwitch retrySwitch = null;
        private MutableTuning tuning = null;
        private Bulkhead bulkhead = null;
        private SingleFlight<T> singleFlight = null;
        private Function<RetryContext<?>, String> coalesceBy = null;
        private AttemptLifecycle<T> lifecycle = null;
        private ResultCache<String, T> cache = null;
        private Function<RetryContext<?>, String> cacheKeyFn = null;
        private Duration cacheTtl = null;
        private RetryEventBus<T> eventBus = null;
        private TraceContext trace = null;

        // ---------- Task / fallback ----------
        private Callable<T> task;
        private Function<Throwable, T> fallback = null;

        // ---------- Health / dynamic tuning ----------
        private HealthProbe healthProbe = null;
        private DynamicTuning dynamicTuning = null;

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
            this.backoff = BackoffStrategy.fixed(Duration.ofMillis(Math.max(0L, millis)));
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

        // Hook sugars
        public Builder<T> onSuccess(Consumer<T> consumer) {
            return onSuccess((result, ctx) -> consumer.accept(result));
        }

        public Builder<T> onFailure(Consumer<Throwable> consumer) {
            return onFailure((exception, ctx) -> consumer.accept(exception));
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

        public Builder<T> budget(RetryBudget budget) {
            this.budget = Objects.requireNonNull(budget, "budget");
            return this;
        }

        public Builder<T> metrics(RetryMetrics metrics) {
            this.metrics = Objects.requireNonNull(metrics, "metrics");
            return this;
        }

        public Builder<T> retryAfter(RetryAfterExtractor<T> retryAfterExtractor) {
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

        public Builder<T> bulkhead(Bulkhead bulkhead) {
            this.bulkhead = bulkhead;
            return this;
        }

        public Builder<T> singleFlight(SingleFlight<T> singleFlight) {
            this.singleFlight = singleFlight;
            return this;
        }

        public Builder<T> coalesceBy(Function<RetryContext<?>, String> coalesceBy) {
            this.coalesceBy = coalesceBy;
            return this;
        }

        public Builder<T> lifecycle(AttemptLifecycle<T> lifecycle) {
            this.lifecycle = lifecycle;
            return this;
        }

        public Builder<T> cache(ResultCache<String, T> cache, Function<RetryContext<?>, String> keyFn, Duration ttl) {
            this.cache = cache;
            this.cacheKeyFn = keyFn;
            this.cacheTtl = ttl;
            return this;
        }

        public Builder<T> eventBus(RetryEventBus<T> bus) {
            this.eventBus = bus;
            return this;
        }

        public Builder<T> trace(TraceContext trace) {
            this.trace = trace;
            return this;
        }

        // Health / dynamic tuning
        public Builder<T> healthProbe(HealthProbe healthProbe) {
            this.healthProbe = healthProbe;
            return this;
        }

        public Builder<T> dynamicTuning(DynamicTuning dynamicTuning) {
            this.dynamicTuning = dynamicTuning;
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
                    stop, backoff, buildPolicy(), listeners, sleeper, clock,
                    budget, metrics, attemptTimeout, attemptExecutor, fallback,
                    backoffRouter, retryAfterExtractor,
                    retrySwitch, tuning, bulkhead,
                    singleFlight, coalesceBy, lifecycle,
                    cache, cacheKeyFn, cacheTtl,
                    eventBus, trace, attemptTimeouts
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

            if (healthProbe != null && dynamicTuning != null) {
                dynamicTuning.apply(healthProbe.state(), this);
            }

            return new RetryExecutor<>(
                    // identity
                    name, id, tags,
                    // timing/stop
                    stop, backoff,
                    // policy
                    buildPolicy(),
                    // infra
                    listeners, sleeper, clock, budget, metrics,
                    // timeouts
                    attemptTimeout, attemptExecutor,
                    // task + fallback
                    task, fallback,
                    // features params
                    backoffRouter,
                    retryAfterExtractor,
                    retrySwitch,
                    tuning,
                    bulkhead,
                    singleFlight, bulkhead,
                    lifecycle,
                    cache, cacheKeyFn, cacheTtl,
                    eventBus,
                    trace,
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
