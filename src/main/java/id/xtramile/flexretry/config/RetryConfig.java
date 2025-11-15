package id.xtramile.flexretry.config;

import id.xtramile.flexretry.RetryContext;
import id.xtramile.flexretry.RetryExecutor;
import id.xtramile.flexretry.RetryListeners;
import id.xtramile.flexretry.Sleeper;
import id.xtramile.flexretry.control.budget.RetryBudget;
import id.xtramile.flexretry.control.bulkhead.Bulkhead;
import id.xtramile.flexretry.control.cache.ResultCache;
import id.xtramile.flexretry.control.sf.SingleFlight;
import id.xtramile.flexretry.control.tuning.MutableTuning;
import id.xtramile.flexretry.control.tuning.RetrySwitch;
import id.xtramile.flexretry.integrations.http.RetryAfterExtractor;
import id.xtramile.flexretry.lifecycle.AttemptLifecycle;
import id.xtramile.flexretry.observability.events.RetryEventBus;
import id.xtramile.flexretry.observability.metrics.RetryMetrics;
import id.xtramile.flexretry.observability.trace.TraceContext;
import id.xtramile.flexretry.strategy.backoff.BackoffRouter;
import id.xtramile.flexretry.strategy.backoff.BackoffStrategy;
import id.xtramile.flexretry.strategy.policy.RetryPolicy;
import id.xtramile.flexretry.strategy.stop.StopStrategy;
import id.xtramile.flexretry.strategy.timeout.AttemptTimeoutStrategy;
import id.xtramile.flexretry.support.time.Clock;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

public final class RetryConfig<T> {

    // ---- Identity / tags ----
    public final String name;
    public final String id;
    public final Map<String, Object> tags;

    // ---- Stop / timing ----
    public final StopStrategy stop;
    public final BackoffStrategy backoff;
    public final BackoffRouter backoffRouter;
    public final AttemptTimeoutStrategy attemptTimeouts;
    public final Duration attemptTimeout;
    public final ExecutorService attemptExecutor;

    // ---- Policy ----
    public final RetryPolicy<T> policy;

    // ---- Infra ----
    public final RetryListeners<T> listeners;
    public final Sleeper sleeper;
    public final Clock clock;
    public final RetryBudget budget;
    public final RetryMetrics metrics;
    public final RetryAfterExtractor<T> retryAfterExtractor;
    public final RetrySwitch retrySwitch;
    public final MutableTuning tuning;
    public final Bulkhead bulkhead;
    public final SingleFlight<T> singleFlight;
    public final Function<RetryContext<?>, String> coalesceBy;
    public final AttemptLifecycle<T> lifecycle;
    public final ResultCache<String, T> cache;
    public final Function<RetryContext<?>, String> cacheKeyFn;
    public final Duration cacheTtl;
    public final RetryEventBus<T> eventBus;
    public final TraceContext trace;

    // ---- Fallback ----
    public final Function<Throwable, T> fallback;

    public RetryConfig(
            // identity
            String name, String id, Map<String, Object> tags,
            // stop/timing
            StopStrategy stop, BackoffStrategy backoff,
            // policy
            RetryPolicy<T> policy,
            // infra core
            RetryListeners<T> listeners, Sleeper sleeper, Clock clock,
            RetryBudget budget, RetryMetrics metrics,
            // fixed timeouts/executor
            Duration attemptTimeout, ExecutorService attemptExecutor,
            // task-independent extras
            Function<Throwable, T> fallback,
            // advanced feature params
            BackoffRouter backoffRouter,
            RetryAfterExtractor<T> retryAfterExtractor,
            RetrySwitch retrySwitch, MutableTuning tuning,
            Bulkhead bulkhead,
            SingleFlight<T> singleFlight, Function<RetryContext<?>, String> coalesceBy,
            AttemptLifecycle<T> lifecycle,
            ResultCache<String, T> cache, Function<RetryContext<?>, String> cacheKeyFn, Duration cacheTtl,
            RetryEventBus<T> eventBus,
            TraceContext trace,
            AttemptTimeoutStrategy attemptTimeouts
    ) {
        // identity
        this.name = Objects.requireNonNull(name, "name");
        this.id = Objects.requireNonNull(id, "id");
        this.tags = Objects.requireNonNull(tags, "tags");

        // timing/stop
        this.stop = Objects.requireNonNull(stop, "stop");
        this.backoff = Objects.requireNonNull(backoff, "backoff");

        // policy
        this.policy = Objects.requireNonNull(policy, "policy");

        // infra core
        this.listeners = Objects.requireNonNullElseGet(listeners, RetryListeners::new);
        this.sleeper = Objects.requireNonNullElseGet(sleeper, Sleeper::system);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::system);
        this.budget = Objects.requireNonNullElseGet(budget, RetryBudget::unlimited);
        this.metrics = Objects.requireNonNullElseGet(metrics, RetryMetrics::noop);

        // timeouts/executor
        this.attemptTimeout = attemptTimeout;
        this.attemptExecutor = attemptExecutor;

        // fallback
        this.fallback = fallback;

        // advanced
        this.backoffRouter = backoffRouter;
        this.retryAfterExtractor = retryAfterExtractor;
        this.retrySwitch = retrySwitch;
        this.tuning = tuning;
        this.bulkhead = bulkhead;
        this.singleFlight = singleFlight;
        this.coalesceBy = coalesceBy;
        this.lifecycle = lifecycle;
        this.cache = cache;
        this.cacheKeyFn = cacheKeyFn;
        this.cacheTtl = cacheTtl;
        this.eventBus = eventBus;
        this.trace = trace;
        this.attemptTimeouts = attemptTimeouts;
    }

    public T run(Callable<T> task) {
        RetryExecutor<T> executor = new RetryExecutor<>(
                name, id, tags,
                stop, backoff,
                policy, listeners,
                sleeper, clock, budget, metrics,
                attemptTimeout, attemptExecutor,
                task, fallback,
                backoffRouter, retryAfterExtractor,
                retrySwitch, tuning, bulkhead,
                singleFlight, coalesceBy, lifecycle,
                cache, cacheKeyFn, cacheTtl,
                eventBus, trace, attemptTimeouts
        );

        return executor.run();
    }

    public CompletableFuture<T> runAsync(Callable<T> task, Executor executor) {
        Objects.requireNonNull(executor, "executor");
        return CompletableFuture.supplyAsync(() -> run(task), executor);
    }
}
