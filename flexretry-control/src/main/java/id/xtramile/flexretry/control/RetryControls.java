package id.xtramile.flexretry.control;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.RetryContext;
import id.xtramile.flexretry.control.breaker.CircuitBreaker;
import id.xtramile.flexretry.control.budget.RetryBudget;
import id.xtramile.flexretry.control.bulkhead.Bulkhead;
import id.xtramile.flexretry.control.bulkhead.BulkheadFullException;
import id.xtramile.flexretry.control.cache.ResultCache;
import id.xtramile.flexretry.control.concurrency.ConcurrencyLimiter;
import id.xtramile.flexretry.control.health.HealthProbe;
import id.xtramile.flexretry.control.ratelimit.RateLimiter;
import id.xtramile.flexretry.control.sf.SingleFlight;
import id.xtramile.flexretry.control.tuning.DynamicTuning;
import id.xtramile.flexretry.control.tuning.MutableTuning;
import id.xtramile.flexretry.control.tuning.RetrySwitch;
import id.xtramile.flexretry.lifecycle.AttemptLifecycle;
import id.xtramile.flexretry.strategy.policy.RetryPolicy;
import id.xtramile.flexretry.strategy.stop.StopStrategy;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class RetryControls {
    private RetryControls() {}

    /* -------------------- BUDGET -------------------- */
    public static <T> RetryPolicy<T> withBudget(RetryPolicy<T> base, RetryBudget budget) {
        Objects.requireNonNull(base);
        Objects.requireNonNull(budget);

        return ((result, error, attempt, maxAttempts) -> {
            if (!base.shouldRetry(result, error, attempt, maxAttempts)) {
                return false;
            }

            return budget.tryAcquire();
        });
    }

    public static <T> Retry.Builder<T> budget(Retry.Builder<T> base, RetryBudget budget) {
        return base.policy(withBudget((result, error, attempt, maxAttempts) -> true, budget));
    }

    /* -------------------- BULKHEAD -------------------- */
    public static <T> Supplier<T> bulkhead(Bulkhead bulkhead, Supplier<T> task) {
        Objects.requireNonNull(bulkhead);
        Objects.requireNonNull(task);

        return () -> {
            if (!bulkhead.tryAcquire()) {
                throw new BulkheadFullException("bulkhead full");
            }

            try {
                return task.get();
            } finally {
                bulkhead.release();
            }
        };
    }

    public static <T> Callable<T> bulkhead(Bulkhead bulkhead, Callable<T> task) {
        Objects.requireNonNull(bulkhead);
        Objects.requireNonNull(task);

        return () -> {
            if (!bulkhead.tryAcquire()) {
                throw new BulkheadFullException("bulkhead full");
            }

            try {
                return task.call();
            } finally {
                bulkhead.release();
            }
        };
    }

    /* -------------------- SINGLE-FLIGHT -------------------- */
    public static <T> Supplier<T> singleFlight(
            SingleFlight<T> sf,
            Function<RetryContext<?>, String> keyFn,
            Supplier<T> task,
            Retry.Builder<T> builder
    ) {
        Objects.requireNonNull(sf);
        Objects.requireNonNull(keyFn);
        Objects.requireNonNull(task);
        Objects.requireNonNull(builder);

        builder.lifecycle(new AttemptLifecycle<T>(){});

        return () -> {
            String key = keyFn.apply(new RetryContext<>(
                    "singleFlight", 1, 1, null, null, Duration.ZERO, Map.of()
            ));

            try {
                return sf.execute(key, task::get);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    /* -------------------- CACHE -------------------- */
    public static <T> void cache(
            Retry.Builder<T> builder,
            ResultCache<String, T> cache,
            Function<RetryContext<?>, String> keyFn,
            Duration ttl
    ) {
        Objects.requireNonNull(builder);
        Objects.requireNonNull(cache);
        Objects.requireNonNull(keyFn);
        Objects.requireNonNull(ttl);

        BiConsumer<T, RetryContext<T>> putter = (result, ctx) -> {
            String key = keyFn.apply(ctx);
            cache.put(key, result, ttl);
        };

        builder.afterAttemptSuccess(putter);
    }

    public static <T> Supplier<T> cachingSupplier(
            ResultCache<String, T> cache,
            Function<RetryContext<?>, String> keyFn,
            Duration ttl,
            Supplier<T> original
    ) {
        Objects.requireNonNull(cache);
        Objects.requireNonNull(keyFn);
        Objects.requireNonNull(ttl);
        Objects.requireNonNull(original);

        final String key = keyFn.apply(new RetryContext<>(
                "cache", 1, 1, null, null, Duration.ZERO, Map.of()
        ));

        return () -> {
            Optional<T> hit = cache.get(key);

            if (hit.isPresent()) {
                return hit.get();
            }

            T result = original.get();
            cache.put(key, result, ttl);

            return result;
        };
    }

    /* -------------------- HEALTH + TUNING -------------------- */
    public static StopStrategy switchStop(RetrySwitch retrySwitch) {
        Objects.requireNonNull(retrySwitch);
        return ((attempt, startNanos, nowNanos, nextDelay) -> !retrySwitch.isOn());
    }

    public static StopStrategy tunedStop(MutableTuning tuning) {
        Objects.requireNonNull(tuning);

        StopStrategy base = StopStrategy.maxAttempts(tuning.maxAttempts());
        if (tuning.maxElapsed() != null) {
            base = StopStrategy.compose(base, StopStrategy.maxElapsed(tuning.maxElapsed()));
        }

        return base;
    }

    public static <T> Retry.Builder<T> applyDynamicTuning(
            Retry.Builder<T> builder,
            HealthProbe health,
            DynamicTuning tuning,
            MutableTuning mutable
    ) {
        Objects.requireNonNull(builder);
        if (health == null || tuning == null || mutable == null) {
            return builder;
        }

        try {
            tuning.apply(health.state(), builder);
        } catch (Throwable ignore) {}

        return builder;
    }

    /* -------------------- RATE LIMITER -------------------- */
    public static <T> Supplier<T> rateLimited(Supplier<T> task, RateLimiter limiter) {
        Objects.requireNonNull(task);
        Objects.requireNonNull(limiter);

        return () -> {
            if (!limiter.tryAcquire()) {
                throw new RuntimeException("rate limit exceeded");
            }

            return task.get();
        };
    }

    /* -------------------- CONCURRENCY -------------------- */
    public static <T> Supplier<T> concurrencyLimited(Supplier<T> task, ConcurrencyLimiter limiter) {
        Objects.requireNonNull(task);
        Objects.requireNonNull(limiter);

        return () -> {
            if (!limiter.tryAcquire()) {
                throw new RuntimeException("concurrency limited");
            }

            try {
                T result = task.get();
                limiter.onSuccess();
                return result;

            } catch (Throwable e) {
                limiter.onDropped();
                throw e;
            }
        };
    }

    /* -------------------- CIRCUIT BREAKER -------------------- */
    public static <T> Supplier<T> circuitBreak(CircuitBreaker breaker, Supplier<T> task) {
        Objects.requireNonNull(breaker);
        Objects.requireNonNull(task);

        return () -> {
            if (!breaker.allow()) {
                throw new RuntimeException("circuit open");
            }

            try {
                T result = task.get();
                breaker.onSuccess();
                return result;

            } catch (Throwable e) {
                breaker.onFailure();
                throw e;
            }
        };
    }

    /* -------------------- HEDGING -------------------- */
    public static <T> Supplier<T> hedged(Supplier<T> primary, Supplier<T> duplicate, long hedgeDelayMillis) {
        Objects.requireNonNull(primary);
        Objects.requireNonNull(duplicate);

        return () -> {
            ExecutorService service = Executors.newCachedThreadPool();
            Future<T> future1 = service.submit(primary::get);

            try {
                return future1.get(hedgeDelayMillis, TimeUnit.MILLISECONDS);

            } catch (TimeoutException exception) {
                Future<T> future2 = service.submit(duplicate::get);

                try {
                    return CompletableFuture.anyOf(
                            CompletableFuture.supplyAsync(() -> getQuiet(future1)),
                            CompletableFuture.supplyAsync(() -> getQuiet(future2))
                    ).thenApply(o -> (T) o).join();

                } finally {
                    service.shutdown();
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    private static <T> T getQuiet(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
