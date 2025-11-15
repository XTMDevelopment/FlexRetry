package id.xtramile.flexretry.config;

import id.xtramile.flexretry.RetryExecutor;
import id.xtramile.flexretry.RetryListeners;
import id.xtramile.flexretry.Sleeper;
import id.xtramile.flexretry.backoff.BackoffStrategy;
import id.xtramile.flexretry.budget.RetryBudget;
import id.xtramile.flexretry.metrics.RetryMetrics;
import id.xtramile.flexretry.policy.RetryPolicy;
import id.xtramile.flexretry.stop.StopStrategy;
import id.xtramile.flexretry.time.Clock;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

public final class RetryConfig<T> {
    public final String name;
    public final String id;
    public final Map<String, Object> tags;
    public final StopStrategy stop;
    public final BackoffStrategy  backoff;
    public final RetryPolicy<T> policy;
    public final RetryListeners<T> listeners;
    public final Sleeper sleeper;
    public final Clock clock;
    public final RetryBudget budget;
    public final RetryMetrics metrics;
    public final Duration attemptTimeout;
    public final ExecutorService attemptExecutor;
    public final Function<Throwable, T> fallback;

    public RetryConfig(
            String name,
            String id,
            Map<String, Object> tags,
            StopStrategy stop,
            BackoffStrategy backoff,
            RetryPolicy<T> policy,
            RetryListeners<T> listeners,
            Sleeper sleeper,
            Clock clock,
            RetryBudget budget,
            RetryMetrics metrics,
            Duration attemptTimeout,
            ExecutorService attemptExecutor,
            Function<Throwable, T> fallback
    ) {
        this.name = Objects.requireNonNull(name);
        this.id = Objects.requireNonNull(id);
        this.tags = Objects.requireNonNull(tags);
        this.stop = Objects.requireNonNull(stop);
        this.backoff = Objects.requireNonNull(backoff);
        this.policy = Objects.requireNonNull(policy);
        this.listeners = Objects.requireNonNull(listeners);
        this.sleeper = Objects.requireNonNull(sleeper);
        this.clock = Objects.requireNonNull(clock);
        this.budget = Objects.requireNonNull(budget);
        this.metrics = Objects.requireNonNull(metrics);
        this.attemptTimeout = attemptTimeout;
        this.attemptExecutor = attemptExecutor;
        this.fallback = fallback;
    }

    public T run(Callable<T> task) {
        RetryExecutor<T> executor = new RetryExecutor<>(
                name, id, tags,
                stop, backoff, policy, listeners,
                sleeper, clock, budget, metrics,
                attemptTimeout, attemptExecutor,
                task, fallback
        );

        return executor.run();
    }

    public CompletableFuture<T> runAsync(Callable<T> task, Executor executor) {
        RetryExecutor<T> exec = new RetryExecutor<>(
                name, id, tags,
                stop, backoff, policy, listeners,
                sleeper, clock, budget, metrics,
                attemptTimeout, attemptExecutor,
                task, fallback
        );

        return CompletableFuture.supplyAsync(exec::run, executor);
    }
}
