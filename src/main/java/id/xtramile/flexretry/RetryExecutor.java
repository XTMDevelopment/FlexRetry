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
import id.xtramile.flexretry.tuning.MutableTuning;
import id.xtramile.flexretry.tuning.RetrySwitch;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Runs the attempt loop using a policy + backoff + listeners
 */
public final class RetryExecutor<T> {
    private final String name;
    private final String id;
    private final Map<String, Object> tags;

    private final StopStrategy stop;
    private final BackoffStrategy backoff;
    private final RetryPolicy<T> policy;
    private final RetryListeners<T> listeners;
    private final Sleeper sleeper;
    private final Clock clock;
    private final RetryBudget budget;
    private final RetryMetrics metrics;

    private final Duration attemptTimeout;
    private final ExecutorService attemptExecutor;
    private final Callable<T> task;
    private final Function<Throwable, T> fallback;

    private final BackoffRouter backoffRouter;
    private final RetryAfterExtractor<T> retryAfterExtractor;
    private final RetrySwitch retrySwitch;
    private final MutableTuning tuning;
    private final Bulkhead bulkhead;

    private final Function<RetryContext<?>, String> coalesceBy;
    private final SingleFlight<T> singleFlight;
    private final AttemptLifecycle<T> lifecycle;

    private final ResultCache<String, T> cache;
    private final Function<RetryContext<?>, String> cacheKeyFn;
    private final Duration cacheTtl;

    private final RetryEventBus<T> eventBus;

    public RetryExecutor(
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
            Callable<T> task,
            Function<Throwable, T> fallback,
            BackoffRouter backoffRouter,
            RetryAfterExtractor<T> retryAfterExtractor,
            RetrySwitch retrySwitch,
            MutableTuning tuning,
            Bulkhead bulkhead,
            Function<RetryContext<?>, String> coalesceBy,
            SingleFlight<T> singleFlight,
            AttemptLifecycle<T> lifecycle,
            ResultCache<String, T> cache,
            Function<RetryContext<?>, String> cacheKeyFn,
            Duration cacheTtl,
            RetryEventBus<T> eventBus
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.id = Objects.requireNonNull(id, "id");
        this.tags = tags == null ? Map.of() : tags;

        this.stop = Objects.requireNonNull(stop, "stop");
        this.backoff = Objects.requireNonNull(backoff, "backoff");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.listeners = Objects.requireNonNullElseGet(listeners, RetryListeners::new);
        this.sleeper = Objects.requireNonNullElseGet(sleeper, Sleeper::system);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::system);
        this.budget = Objects.requireNonNullElseGet(budget, RetryBudget::unlimited);
        this.metrics = Objects.requireNonNullElseGet(metrics, RetryMetrics::noop);

        this.attemptTimeout = attemptTimeout;
        this.attemptExecutor = attemptExecutor;
        this.task = Objects.requireNonNull(task, "task");
        this.fallback = fallback;
        this.backoffRouter = backoffRouter;
        this.retryAfterExtractor = retryAfterExtractor;
        this.retrySwitch = retrySwitch;
        this.tuning = tuning;
        this.bulkhead = bulkhead;

        this.coalesceBy = coalesceBy;
        this.singleFlight = singleFlight;
        this.lifecycle = lifecycle;

        this.cache = cache;
        this.cacheKeyFn = cacheKeyFn;
        this.cacheTtl = cacheTtl;

        this.eventBus = eventBus;
    }

    public T run() {
        T lastResult = null;
        Throwable lastError = null;
        int finalAttempt = 0;

        long start = clock.nanoTime();

        try {
            for (int attempt = 1; ; attempt++) {
                if (bulkhead != null && !bulkhead.tryAcquire()) {
                    finalAttempt = Math.max(1, attempt - 1);
                    throw new RetryException("Bulkhead full; cannot acquire", null, finalAttempt);
                }

                Duration nextDelay;

                if (lastError != null && backoffRouter != null) {
                    nextDelay = backoffRouter.select(lastError).delayForAttempt(attempt);
                } else {
                    nextDelay = backoff.delayForAttempt(attempt);
                }

                RetryContext<T> ctxBefore = new RetryContext<>(id, attempt, Integer.MAX_VALUE, lastResult, lastError, nextDelay, tags);

                if (lifecycle != null) {
                    lifecycle.beforeAttempt(ctxBefore);
                }

                if (eventBus != null) {
                    eventBus.publish(new RetryEvent.AttemptStarted<>(ctxBefore));
                }

                if (cache != null && cacheKeyFn != null) {
                    String key = cacheKeyFn.apply(ctxBefore);
                    Optional<T> hit = cache.get(key);

                    if (hit.isPresent()) {
                        T result = hit.get();
                        listeners.onSuccess.accept(result, new RetryContext<>(id, attempt, attempt, result, null, Duration.ZERO, tags));
                        metrics.attemptSucceeded(name, attempt);

                        return result;
                    }
                }

                listeners.onAttempt.accept(ctxBefore);
                metrics.attemptStarted(name, attempt);

                long now = clock.nanoTime();
                if (effectiveStop(stop).shouldStop(attempt, start, now, nextDelay) && attempt > 1) {
                    finalAttempt = attempt - 1;

                    RetryContext<T> ctxFail = new RetryContext<>(id, finalAttempt, finalAttempt, lastResult, lastError, Duration.ZERO, tags);
                    listeners.onFailure.accept(lastError, ctxFail);
                    metrics.exhausted(name, finalAttempt, lastError);

                    if (fallback != null && eventBus != null) {
                        listeners.onRecover.accept(ctxFail);
                        eventBus.publish(new RetryEvent.Recovered<>(ctxFail, fallback.apply(lastError)));
                        return fallback.apply(lastError);
                    }

                    if (eventBus != null) {
                        eventBus.publish(new RetryEvent.Exhausted<>(ctxFail, lastError));
                    }

                    throw new RetryException("Retry exhausted at attempt " + finalAttempt, lastError, finalAttempt);
                }

                try {
                    T result;

                    if (coalesceBy != null && singleFlight != null) {
                        String key = coalesceBy.apply(ctxBefore);
                        result = singleFlight.execute(key, this::executeAttempt);
                    } else {
                        result = executeAttempt();
                    }

                    lastResult = result;
                    lastError = null;

                    boolean again = policy.shouldRetry(result, null, attempt, Integer.MAX_VALUE);
                    if (again) {
                        if (!budget.tryAcquire()) {
                            finalAttempt = attempt;

                            RetryContext<T> ctxFail = new RetryContext<>(id, finalAttempt, finalAttempt, result, null, Duration.ZERO, tags);
                            listeners.onFailure.accept(null, ctxFail);
                            metrics.exhausted(name, finalAttempt, null);

                            if (fallback != null && eventBus != null) {
                                listeners.onRecover.accept(ctxFail);
                                eventBus.publish(new RetryEvent.Recovered<>(ctxFail, fallback.apply(null)));
                                return fallback.apply(null);
                            }

                            if (eventBus != null) {
                                eventBus.publish(new RetryEvent.Exhausted<>(ctxFail, null));
                            }

                            throw new RetryException("Retry denied by budget at attempt " + attempt, null, attempt);
                        }

                        Duration adjusted = nextDelay;
                        if (retryAfterExtractor != null) {
                            Duration hint = retryAfterExtractor.extract(lastError, lastResult);

                            if (hint != null) {
                                adjusted = hint;
                            }
                        }

                        adjusted = listeners.beforeSleep.apply(adjusted, ctxBefore);
                        sleeper.sleep(adjusted);
                        continue;
                    }

                    finalAttempt = attempt;

                    RetryContext<T> ctxSuccess = new RetryContext<>(id, attempt, attempt, result, null, Duration.ZERO, tags);
                    listeners.onSuccess.accept(result, ctxSuccess);
                    metrics.attemptSucceeded(name, attempt);

                    if (eventBus != null) {
                        eventBus.publish(new RetryEvent.AttemptSucceeded<>(ctxSuccess, result));
                    }

                    if (bulkhead != null) {
                        bulkhead.release();
                    }

                    if (lifecycle != null) {
                        lifecycle.afterSuccess(ctxSuccess);
                    }

                    if (cache != null && cacheKeyFn != null && cacheTtl != null) {
                        String key = cacheKeyFn.apply(ctxSuccess);

                        try {
                            cache.put(key, result, cacheTtl);
                        } catch (Throwable ignore) {}
                    }

                    return result;

                } catch (Throwable e) {
                    lastError = unwrap(e);

                    listeners.afterAttemptFailure.accept(lastError, ctxBefore);

                    boolean again = policy.shouldRetry(null, lastError, attempt, Integer.MAX_VALUE);
                    if (again) {
                        if (!budget.tryAcquire()) {
                            finalAttempt = attempt;

                            RetryContext<T> ctxFail = new RetryContext<>(id, finalAttempt, finalAttempt, lastResult, lastError, Duration.ZERO, tags);
                            listeners.onFailure.accept(lastError, ctxFail);
                            metrics.exhausted(name, finalAttempt, lastError);

                            if (fallback != null && eventBus != null) {
                                listeners.onRecover.accept(ctxFail);
                                eventBus.publish(new RetryEvent.Recovered<>(ctxFail, fallback.apply(lastError)));
                                return fallback.apply(lastError);
                            }

                            if (eventBus != null) {
                                eventBus.publish(new RetryEvent.AttemptFailed<>(ctxBefore, lastError));
                            }

                            throw new RetryException("Retry denied by budget at attempt " + attempt, lastError, attempt);
                        }

                        Duration adjusted = nextDelay;
                        if (retryAfterExtractor != null) {
                            Duration hint = retryAfterExtractor.extract(lastError, lastResult);

                            if (hint != null) {
                                adjusted = hint;
                            }
                        }

                        adjusted = listeners.beforeSleep.apply(adjusted, ctxBefore);
                        sleeper.sleep(adjusted);
                        continue;
                    }

                    finalAttempt = attempt;

                    RetryContext<T> ctxFail = new RetryContext<>(id, finalAttempt, finalAttempt, lastResult, lastError, Duration.ZERO, tags);
                    listeners.onFailure.accept(lastError, ctxFail);
                    metrics.attemptFailed(name, finalAttempt, lastError);

                    if (eventBus != null) {
                        eventBus.publish(new RetryEvent.AttemptFailed<>(ctxFail, lastError));
                    }

                    if (fallback != null && eventBus != null) {
                        listeners.onRecover.accept(ctxFail);
                        eventBus.publish(new RetryEvent.Recovered<>(ctxFail, fallback.apply(lastError)));
                        return fallback.apply(lastError);
                    }

                    if (bulkhead != null) {
                        bulkhead.release();
                    }

                    if (lifecycle != null) {
                        lifecycle.afterFailure(ctxBefore, lastError);
                    }

                    throw new RetryException("Retry failed after " + attempt + " attempt(s)", lastError, attempt);
                }
            }

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();

            finalAttempt = Math.max(1, finalAttempt);

            RetryContext<T> ctxFail = new RetryContext<>(id, finalAttempt, finalAttempt, lastResult, ie, Duration.ZERO, tags);
            listeners.onFailure.accept(null, ctxFail);
            metrics.attemptFailed(name, finalAttempt, ie);

            if (eventBus != null) {
                eventBus.publish(new RetryEvent.AttemptFailed<>(ctxFail, null));
            }

            if (fallback != null && eventBus != null) {
                listeners.onRecover.accept(ctxFail);
                eventBus.publish(new RetryEvent.Recovered<>(ctxFail, fallback.apply(null)));
                return fallback.apply(ie);
            }

        } finally {
            int att = (finalAttempt == 0) ? 1 : finalAttempt;
            listeners.onFinally.accept(new RetryContext<>(id, att, att, lastResult, lastError, Duration.ZERO, tags));

            if (bulkhead != null) {
                bulkhead.release();
            }
        }
    }

    private T executeAttempt() throws Exception {
        if (attemptTimeout == null) {
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

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof RuntimeException && throwable.getCause() != null) {
            return throwable.getCause();
        }

        return throwable;
    }
}
