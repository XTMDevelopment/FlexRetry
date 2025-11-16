package id.xtramile.flexretry.config;

import id.xtramile.flexretry.RetryExecutor;
import id.xtramile.flexretry.RetryListeners;
import id.xtramile.flexretry.Sleeper;
import id.xtramile.flexretry.lifecycle.AttemptLifecycle;
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
    public final AttemptLifecycle<T> lifecycle;

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
            // fixed timeouts/executor
            Duration attemptTimeout, ExecutorService attemptExecutor,
            // task-independent extras
            Function<Throwable, T> fallback,
            // advanced feature params
            BackoffRouter backoffRouter,
            AttemptLifecycle<T> lifecycle,
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

        // timeouts/executor
        this.attemptTimeout = attemptTimeout;
        this.attemptExecutor = attemptExecutor;

        // fallback
        this.fallback = fallback;

        // advanced
        this.backoffRouter = backoffRouter;
        this.lifecycle = lifecycle;
        this.attemptTimeouts = attemptTimeouts;
    }

    public T run(Callable<T> task) {
        RetryExecutor<T> executor = new RetryExecutor<>(
                name, id, tags,
                stop, backoff,
                policy,
                listeners, sleeper, clock,
                attemptTimeout, attemptExecutor,
                task, fallback,
                backoffRouter,
                lifecycle,
                attemptTimeouts
        );

        return executor.run();
    }

    public CompletableFuture<T> runAsync(Callable<T> task, Executor executor) {
        Objects.requireNonNull(executor, "executor");
        return CompletableFuture.supplyAsync(() -> run(task), executor);
    }
}
