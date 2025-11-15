package id.xtramile.flexretry;

import id.xtramile.flexretry.backoff.BackoffRouter;
import id.xtramile.flexretry.backoff.BackoffStrategy;
import id.xtramile.flexretry.budget.RetryBudget;
import id.xtramile.flexretry.bulkhead.Bulkhead;
import id.xtramile.flexretry.cache.ResultCache;
import id.xtramile.flexretry.events.RetryEvent;
import id.xtramile.flexretry.events.RetryEventBus;
import id.xtramile.flexretry.http.RetryAfterExtractor;
import id.xtramile.flexretry.lifecycle.AttemptLifecycle;
import id.xtramile.flexretry.metrics.RetryMetrics;
import id.xtramile.flexretry.policy.RetryPolicy;
import id.xtramile.flexretry.sf.SingleFlight;
import id.xtramile.flexretry.stop.CompositeStop;
import id.xtramile.flexretry.stop.FixedAttemptsStop;
import id.xtramile.flexretry.stop.MaxElapsedStop;
import id.xtramile.flexretry.stop.StopStrategy;
import id.xtramile.flexretry.time.Clock;
import id.xtramile.flexretry.timeouts.AttemptTimeoutStrategy;
import id.xtramile.flexretry.trace.TraceContext;
import id.xtramile.flexretry.tuning.MutableTuning;
import id.xtramile.flexretry.tuning.RetrySwitch;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Runs the attempt loop using a policy + backoff + listeners
 */
public final class RetryExecutor<T> {

    // ---- Identity / tags ----
    private final String name;
    private final String id;
    private final Map<String, Object> tags;

    // ---- Stop / timing ----
    private final StopStrategy stop;
    private final BackoffStrategy backoff;
    private final BackoffRouter backoffRouter;

    // ---- Policy ----
    private final RetryPolicy<T> policy;

    // ---- Infra core ----
    private final RetryListeners<T> listeners;
    private final Sleeper sleeper;
    private final Clock clock;
    private final RetryBudget budget;
    private final RetryMetrics metrics;

    // ---- Timeouts / executor ----
    private final Duration attemptTimeout;
    private final ExecutorService attemptExecutor;
    private final AttemptTimeoutStrategy attemptTimeouts;

    // ---- Task / fallback ----
    private final Callable<T> task;
    private final Function<Throwable, T> fallback;

    // ---- Advanced features ----
    private final RetryAfterExtractor<T> retryAfterExtractor;
    private final RetrySwitch retrySwitch;
    private final MutableTuning tuning;
    private final Bulkhead bulkhead;
    private final SingleFlight<T> singleFlight;
    private final Function<RetryContext<?>, String> coalesceBy;
    private final AttemptLifecycle<T> lifecycle;
    private final ResultCache<String, T> cache;
    private final Function<RetryContext<?>, String> cacheKeyFn;
    private final Duration cacheTtl;
    private final RetryEventBus<T> eventBus;
    private final TraceContext trace;


    public RetryExecutor(
            // identity
            String name, String id, Map<String, Object> tags,
            // timing/stop/backoff
            StopStrategy stop, BackoffStrategy backoff,
            // policy
            RetryPolicy<T> policy,
            // infra
            RetryListeners<T> listeners, Sleeper sleeper, Clock clock, RetryBudget budget, RetryMetrics metrics,
            // timeouts / executor
            Duration attemptTimeout, ExecutorService attemptExecutor,
            // task + fallback
            Callable<T> task, Function<Throwable, T> fallback,
            // advanced params
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
        this.tags = tags == null ? Map.of() : tags;

        // timing/stop/backoff
        this.stop = Objects.requireNonNull(stop, "stop");
        this.backoff = Objects.requireNonNull(backoff, "backoff");
        this.backoffRouter = backoffRouter;

        // policy
        this.policy = Objects.requireNonNull(policy, "policy");

        // infra
        this.listeners = Objects.requireNonNullElseGet(listeners, RetryListeners::new);
        this.sleeper = Objects.requireNonNullElseGet(sleeper, Sleeper::system);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::system);
        this.budget = Objects.requireNonNullElseGet(budget, RetryBudget::unlimited);
        this.metrics = Objects.requireNonNullElseGet(metrics, RetryMetrics::noop);

        // timeouts/executor
        this.attemptTimeout = attemptTimeout; // may be null
        this.attemptExecutor = attemptExecutor; // may be null
        this.attemptTimeouts = attemptTimeouts; // may be null

        // task/fallback
        this.task = Objects.requireNonNull(task, "task");
        this.fallback = fallback; // may be null

        // advanced
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
    }

    public T run() {
        T lastResult = null;
        Throwable lastError = null;
        int finalAttempt = 0;

        long startNanos = clock.nanoTime();

        try {
            for (int attempt = 1; ; attempt++) {
                Duration nextDelay;
                if (lastError != null && backoffRouter != null) {
                    nextDelay = backoffRouter.select(lastError).delayForAttempt(attempt);
                } else {
                    nextDelay = backoff.delayForAttempt(attempt);
                }

                RetryContext<T> ctxBefore = new RetryContext<>(id, attempt, Integer.MAX_VALUE, lastResult, lastError, nextDelay, tags);

                long now = clock.nanoTime();
                if (attempt > 1 && effectiveStop(stop).shouldStop(attempt, startNanos, now, nextDelay)) {
                    finalAttempt = attempt - 1;

                    RetryContext<T> ctxFail = new RetryContext<>(id, finalAttempt, finalAttempt, lastResult, lastError, Duration.ZERO, tags);
                    safeRun(() -> listeners.onFailure(lastError, ctxFail));
                    metrics.exhausted(name, finalAttempt, lastError);

                    if (eventBus != null) {
                        safeRun(() -> eventBus.publish(new RetryEvent.Exhausted<>(ctxFail, lastError)));
                    }

                    if (fallback != null) {
                        safeRun(() -> listeners.onRecover.accept(ctxFail));
                        return fallback.apply(lastError);
                    }

                    throw new RetryException("Retry exhausted at attempt " + finalAttempt, lastError, finalAttempt);
                }

                safeRun(() -> listeners.onAttempt.accept(ctxBefore));
                metrics.attemptStarted(name, attempt);

                if (eventBus != null) {
                    eventBus.publish(new RetryEvent.AttemptStarted<>(ctxBefore));
                }

                boolean bulkheadAcquired = false;
                if (bulkhead != null) {
                    bulkheadAcquired = bulkhead.tryAcquire();

                    if (!bulkheadAcquired) {
                        finalAttempt = Math.max(1, attempt - 1);

                        RetryContext<T> ctxFail = new RetryContext<>(id, finalAttempt, finalAttempt, lastResult, lastError, Duration.ZERO, tags);
                        safeRun(() -> listeners.onFailure.accept(lastError, ctxFail));
                        metrics.exhausted(name, finalAttempt, lastError);

                        if (eventBus != null) {
                            safeRun(() -> eventBus.publish(RetryEvent.Exhausted(ctxFail, lastError)));
                        }

                        if (fallback != null) {
                            safeRun(() -> listeners.onRecover.accept(ctxFail));
                            return fallback.apply(lastError);
                        }

                        throw new RetryException("Bulkhead full; cannot acquire", null, finalAttempt);
                    }
                }

                if (trace != null) {
                    safeRun(() -> trace.enter(ctxBefore));
                }

                if (lifecycle != null) {
                    lifecycle.beforeAttempt(ctxBefore);
                }

                if (cache != null && cacheKeyFn != null) {
                    String key = nullSafe(() -> cacheKeyFn.apply(ctxBefore));

                    if (key != null) {
                        try {
                            Optional<T> hit = cache.get(key);

                            if (hit.isPresent()) {
                                T result = hit.get();
                                finalAttempt = attempt;

                                RetryContext<T> ctxSuccess = new RetryContext<>(id, attempt, attempt, result, null, Duration.ZERO, tags);
                                safeRun(() -> listeners.afterAttemptSuccess.accept(result, ctxBefore));
                                safeRun(() -> listeners.onSuccess.accept(result, ctxSuccess));
                                metrics.attemptSucceeded(name, attempt);

                                if (eventBus != null) {
                                    safeRun(() -> eventBus.publish(new RetryEvent.AttemptSucceeded<>(ctxSuccess, result)));
                                }

                                if (lifecycle != null) {
                                    safeRun(() -> lifecycle.afterSuccess(ctxSuccess));
                                }

                                return result;
                            }

                        } catch (Throwable ignore) {}
                    }
                }

                try {
                    T result;
                    if (coalesceBy != null && singleFlight != null) {
                        String key = nullSafe(() -> coalesceBy.apply(ctxBefore));

                        if (key != null) {
                            result = singleFlight.execute(key, () -> executeAttempt(attempt));
                        } else {
                            result = executeAttempt(attempt);
                        }

                    } else {
                        result = executeAttempt(attempt);
                    }

                    lastResult = result;
                    lastError = null;

                    safeRun(() -> listeners.afterAttemptSuccess.accept(result, ctxBefore));

                    boolean again = policy.shouldRetry(result, null, attempt, Integer.MAX_VALUE);
                    if (again) {
                        if (!budget.tryAcquire()) {
                            finalAttempt = attempt;

                            RetryContext<T> ctxFail = new RetryContext<>(id, finalAttempt, finalAttempt, result, null, Duration.ZERO, tags);
                            safeRun(() -> listeners.onFailure.accept(null, ctxFail));
                            metrics.exhausted(name, finalAttempt, null);

                            if (eventBus != null) {
                                safeRun(() -> eventBus.publish(new RetryEvent.Exhausted<>(ctxFail, null)));
                            }

                            if (fallback != null) {
                                safeRun(() -> listeners.onRecover.accept(ctxFail));
                                return fallback.apply(null);
                            }

                            throw new RetryException("Retry denied by budget at attempt " + attempt, null, attempt);
                        }

                        Duration adjusted = nextDelay;
                        if (retryAfterExtractor != null) {
                            Duration hint = nullSafe(() -> retryAfterExtractor.extract(lastError, result));

                            if (hint != null) {
                                adjusted = hint;
                            }
                        }

                        Duration finalDelay = adjusted;
                        adjusted = nullSafe(() -> listeners.beforeSleep.apply(finalDelay, ctxBefore));

                        sleeper.sleep(adjusted);
                        continue;
                    }

                    finalAttempt = attempt;

                    RetryContext<T> ctxSuccess = new RetryContext<>(id, attempt, attempt, result, null, Duration.ZERO, tags);
                    safeRun(() -> listeners.onSuccess.accept(result, ctxSuccess));
                    metrics.attemptSucceeded(name, attempt);

                    if (eventBus != null) {
                        safeRun(() -> eventBus.publish(new RetryEvent.AttemptSucceeded<>(ctxSuccess, result)));
                    }

                    if (lifecycle != null) {
                        safeRun(() -> lifecycle.afterSuccess(ctxSuccess));
                    }

                    if (cache != null && cacheKeyFn != null && cacheTtl != null) {
                        String key = nullSafe(() -> cacheKeyFn.apply(ctxSuccess));

                        try {
                            safeRun(() -> cache.put(key, result, cacheTtl));
                        } catch (Throwable ignore) {}
                    }

                    return result;

                } catch (Throwable e) {
                    lastError = unwrap(e);

                    safeRun(() -> listeners.afterAttemptFailure.accept(lastError, ctxBefore));

                    if (lifecycle != null) {
                        safeRun(() -> lifecycle.afterFailure(ctxBefore, lastError));
                    }

                    boolean again = policy.shouldRetry(null, lastError, attempt, Integer.MAX_VALUE);
                    if (again) {
                        if (!budget.tryAcquire()) {
                            finalAttempt = attempt;

                            RetryContext<T> ctxFail = new RetryContext<>(id, finalAttempt, finalAttempt, lastResult, lastError, Duration.ZERO, tags);
                            safeRun(() -> listeners.onFailure.accept(lastError, ctxFail));
                            metrics.exhausted(name, finalAttempt, lastError);

                            if (eventBus != null) {
                                safeRun(() -> eventBus.publish(new RetryEvent.Exhausted<>(ctxFail, lastError)));
                            }

                            if (fallback != null) {
                                safeRun(() -> listeners.onRecover.accept(ctxFail));
                                return fallback.apply(lastError);
                            }

                            throw new RetryException("Retry denied by budget at attempt " + attempt, lastError, attempt);
                        }

                        Duration adjusted = nextDelay;
                        if (retryAfterExtractor != null) {
                            Duration hint = nullSafe(() -> retryAfterExtractor.extract(lastError, null));

                            if (hint != null) {
                                adjusted = hint;
                            }
                        }

                        Duration finalDelay = adjusted;
                        adjusted = nullSafe(() -> listeners.beforeSleep.apply(finalDelay, ctxBefore), finalDelay);

                        sleeper.sleep(adjusted);
                        continue;
                    }

                    finalAttempt = attempt;

                    RetryContext<T> ctxFail = new RetryContext<>(id, finalAttempt, finalAttempt, lastResult, lastError, Duration.ZERO, tags);
                    safeRun(() -> listeners.onFailure.accept(lastError, ctxFail));
                    metrics.attemptFailed(name, attempt, lastError);

                    if (eventBus != null) {
                        safeRun(() -> eventBus.publish(new RetryEvent.AttemptFailed<>(ctxBefore, lastError)));
                    }

                    if (fallback != null) {
                        safeRun(() -> listeners.onRecover.accept(ctxFail));
                        return fallback.apply(lastError);
                    }

                    throw new RetryException("Retry failed after " + attempt + " attempt(s)", lastError, attempt);

                } finally {
                    if (trace != null) {
                        safeRun(() -> trace.exit(ctxBefore));
                    }

                    if (bulkhead != null) {
                        try {
                            bulkhead.release();
                        } catch (Throwable ignore) {}
                    }
                }
            }

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();

            int att = finalAttempt == 0 ? 1 : finalAttempt;

            RetryContext<T> ctxFail = new RetryContext<>(id, att, att, null, ie, Duration.ZERO, tags);
            safeRun(() -> listeners.onFailure.accept(ie, ctxFail));
            metrics.attemptFailed(name, att, ie);

            if (eventBus != null) {
                safeRun(() -> eventBus.publish(new RetryEvent.AttemptFailed<>(ctxFail, ie)));
            }

            if (fallback != null) {
                safeRun(() -> listeners.onRecover.accept(ctxFail));
                return fallback.apply(ie);
            }

            throw new RetryException("Interrupted during retry", ie, att);

        } finally {
            int att = finalAttempt == 0 ? 1 : finalAttempt;
            safeRun(() -> listeners.onFinally.accept(new RetryContext<>(id, att, att, null, null, Duration.ZERO, tags)));
        }
    }

    private StopStrategy effectiveStop(StopStrategy base) {
        StopStrategy stop = base;

        if (tuning != null) {
            List<StopStrategy> list = new ArrayList<>();
            list.add(new FixedAttemptsStop(tuning.maxAttempts()));

            if (tuning.maxElapsed() != null) {
                list.add(new MaxElapsedStop(tuning.maxElapsed()));
            }

            stop = new CompositeStop(list.toArray(new StopStrategy[0]));
        }

        if (retrySwitch != null) {
            StopStrategy sw = ((attempt, startNanos, nowNanos, nextDelay) -> !retrySwitch.isOn());
            stop = new CompositeStop(stop, sw);
        }

        return stop;
    }

    private T executeAttempt(int attemptIdx) throws Exception {
        Duration perAttempt = attemptTimeouts != null ? attemptTimeouts.timeoutForAttempt(attemptIdx) : attemptTimeout;

        if (perAttempt == null) {
            return task.call();
        }

        ExecutorService exec = attemptExecutor != null ? attemptExecutor : ForkJoinPool.commonPool();
        Future<T> future = exec.submit(task);

        try {
            return future.get(attemptTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            future.cancel(true);
            throw te;
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof RuntimeException && throwable.getCause() != null) {
            return throwable.getCause();
        } else if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        } else if (throwable instanceof RuntimeException && throwable.getCause() != null) {
            return throwable.getCause().getCause();
        }

        return throwable;
    }

    private static void safeRun(Runnable r) {
        try {
            r.run();
        } catch (Throwable ignore) {}
    }

    private static <R> R nullSafe(SupplierWithException<R> supplier) {
        try {
            return supplier.get();
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static <R> R nullSafe(SupplierWithException<R> supplier, R fallback) {
        try {
            R result = supplier.get();
            return result == null ? fallback : result;
        } catch (Throwable ignore) {
            return fallback;
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<R> {
        R get() throws Exception;
    }
}
