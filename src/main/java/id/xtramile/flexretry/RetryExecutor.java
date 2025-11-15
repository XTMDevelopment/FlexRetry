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
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

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
                final Duration nextDelay = computeNextDelay(attempt, lastError);
                final RetryContext<T> ctxBefore = buildContext(attempt, lastResult, lastError, nextDelay);

                if (shouldStopBeforeAttempt(attempt, startNanos, nextDelay)) {
                    return handleExhausted("Retry exhausted at attempt " + (attempt - 1), lastResult, lastError, attempt - 1);
                }

                announceAttempt(ctxBefore, attempt);

                if (!acquireBulkheadIfAny(attempt, lastResult, lastError)) {
                    return handleExhausted("Bulkhead full; cannot acquire", lastResult, lastError, Math.max(1, attempt - 1));
                }

                enterTraceAndLifecycle(ctxBefore);

                T cached = tryHitCache(ctxBefore, attempt);
                if (cached != null) {
                    return cached;
                }

                try {
                    T result = executeWithSingleFlight(attempt, ctxBefore);
                    lastResult = result;
                    lastError = null;

                    afterAttemptSuccess(ctxBefore, result);

                    if (policy.shouldRetry(result, null, attempt, Integer.MAX_VALUE)) {
                        T budgetResult = tryAcquireBudgetOrFail(attempt, result, null);
                        if (budgetResult != null) {
                            return budgetResult;
                        }

                        sleepAdjusted(nextDelay, ctxBefore, null, result);
                        continue;
                    }

                    finalAttempt = attempt;
                    return finalizeSuccess(attempt, result);

                } catch (Throwable e) {
                    lastError = unwrap(e);
                    afterAttemptFailure(ctxBefore, lastError);

                    if (policy.shouldRetry(null, lastError, attempt, Integer.MAX_VALUE)) {
                        T budgetResult = tryAcquireBudgetOrFail(attempt, lastResult, lastError);
                        if (budgetResult != null) {
                            return budgetResult;
                        }

                        sleepAdjusted(nextDelay, ctxBefore, lastError, null);
                        continue;
                    }

                    finalAttempt = attempt;
                    return finalizeFailure(attempt, lastResult, lastError);

                } finally {
                    exitTrace();
                    releaseBulkheadIfAny();
                }
            }

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();

            int att = finalAttempt == 0 ? 1 : finalAttempt;
            return handleInterrupted(att, ie);

        } finally {
            int att = finalAttempt == 0 ? 1 : finalAttempt;
            safeRun(() -> listeners.onFinally.accept(new RetryContext<>(id, att, att, null, null, Duration.ZERO, tags)));
        }
    }

    private Duration computeNextDelay(int attempt, Throwable lastError) {
        if (lastError != null && backoffRouter != null) {
            return backoffRouter.select(lastError).delayForAttempt(attempt);
        }

        return backoff.delayForAttempt(attempt);
    }

    private RetryContext<T> buildContext(int attempt, T lastResult, Throwable lastError, Duration nextDelay) {
        return new RetryContext<>(id, attempt, Integer.MAX_VALUE, lastResult, lastError, nextDelay, tags);
    }

    private boolean shouldStopBeforeAttempt(int attempt, long startNanos, Duration nextDelay) {
        if (attempt <= 1) {
            return false;
        }

        long now = clock.nanoTime();
        return effectiveStop(stop).shouldStop(attempt, startNanos, now, nextDelay);
    }

    private void announceAttempt(RetryContext<T> ctxBefore, int attempt) {
        safeRun(() -> listeners.onAttempt.accept(ctxBefore));
        metrics.attemptStarted(name, attempt);

        if (eventBus != null) {
            safeRun(() -> eventBus.publish(new RetryEvent.AttemptStarted<>(ctxBefore)));
        }
    }

    private boolean acquireBulkheadIfAny(int attempt, T lastResult, Throwable lastError) {
        if (bulkhead == null) {
            return true;
        }

        boolean ok = bulkhead.tryAcquire();
        if (!ok) {
            int failedAttempt = Math.max(1, attempt - 1);
            RetryContext<T> ctxFail = new RetryContext<>(id, failedAttempt, failedAttempt, lastResult, lastError, Duration.ZERO, tags);
            safeRun(() -> listeners.onFailure.accept(lastError, ctxFail));
            metrics.exhausted(name, failedAttempt, lastError);

            if (eventBus != null) {
                safeRun(() -> eventBus.publish(new RetryEvent.Exhausted<>(ctxFail, lastError)));
            }
        }

        return ok;
    }

    private void releaseBulkheadIfAny() {
        if (bulkhead != null) {
            try {
                bulkhead.release();
            } catch (Throwable ignore) {}
        }
    }

    private void enterTraceAndLifecycle(RetryContext<T> ctxBefore) {
        if (trace != null) {
            safeRun(() -> trace.enter(ctxBefore));
        }

        if (lifecycle != null) {
            safeRun(() -> lifecycle.beforeAttempt(ctxBefore));
        }
    }

    private void exitTrace() {
        if (trace != null) {
            try {
                trace.exit(null);
            } catch (Throwable ignore) {}
        }
    }

    private T tryHitCache(RetryContext<T> ctxBefore, int attempt) {
        if (cache == null || cacheKeyFn == null) {
            return null;
        }

        String key = nullSafe(() -> cacheKeyFn.apply(ctxBefore));
        if (key == null) {
            return null;
        }

        try {
            Optional<T> opt = cache.get(key);
            if (opt.isPresent()) {
                T result = opt.get();
                safeRun(() -> listeners.afterAttemptSuccess.accept(result, ctxBefore));
                return handleSuccess(attempt, result);
            }
        } catch (Throwable ignore) {}

        return null;
    }

    private T executeWithSingleFlight(int attempt, RetryContext<T> ctxBefore) throws Exception {
        if (coalesceBy != null && singleFlight != null) {
            String key = nullSafe(() -> coalesceBy.apply(ctxBefore));

            if (key != null) {
                return singleFlight.execute(key, () -> executeAttempt(attempt));
            }
        }

        return executeAttempt(attempt);
    }

    private void afterAttemptSuccess(RetryContext<T> ctxBefore, T result) {
        safeRun(() -> listeners.afterAttemptSuccess.accept(result, ctxBefore));
    }

    private void afterAttemptFailure(RetryContext<T> ctxBefore, Throwable error) {
        safeRun(() -> listeners.afterAttemptFailure.accept(error, ctxBefore));

        if (lifecycle != null) {
            safeRun(() -> lifecycle.afterFailure(ctxBefore, error));
        }
    }

    private T tryAcquireBudgetOrFail(int attempt, T lastResult, Throwable lastError) {
        if (budget.tryAcquire()) {
            return null; // Budget acquired, continue retry
        }

        return handleFailureWithFallback(attempt, lastResult, lastError, 
            (ctx, err) -> metrics.exhausted(name, attempt, err),
                RetryEvent.Exhausted::new,
            "Retry denied by budget at attempt " + attempt);
    }

    private void sleepAdjusted(Duration proposed, RetryContext<T> ctx, Throwable error, T result) throws InterruptedException {
        Duration adjusted = proposed;

        if (retryAfterExtractor != null) {
            Duration hint = (error != null)
                    ? nullSafe(() -> retryAfterExtractor.extract(error, null))
                    : nullSafe(() -> retryAfterExtractor.extract(null, result));

            if (hint != null) {
                adjusted = hint;
            }
        }

        Duration finalDelay = adjusted;
        adjusted = nullSafe(() -> listeners.beforeSleep.apply(finalDelay, ctx), finalDelay);

        sleeper.sleep(adjusted);
    }

    private T finalizeSuccess(int attempt, T result) {
        T finalResult = handleSuccess(attempt, result);

        if (cache != null && cacheKeyFn != null && cacheTtl != null) {
            RetryContext<T> ctxSuccess = new RetryContext<>(id, attempt, attempt, result, null, Duration.ZERO, tags);
            String key = nullSafe(() -> cacheKeyFn.apply(ctxSuccess));

            try {
                safeRun(() -> cache.put(key, result, cacheTtl));
            } catch (Throwable ignore) {}
        }

        return finalResult;
    }

    private T handleSuccess(int attempt, T result) {
        RetryContext<T> ctxSuccess = new RetryContext<>(id, attempt, attempt, result, null, Duration.ZERO, tags);
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

    private T finalizeFailure(int attempt, T lastResult, Throwable lastError) {
        return handleFailureWithFallback(attempt, lastResult, lastError,
            (ctx, err) -> metrics.attemptFailed(name, attempt, err),
                RetryEvent.AttemptFailed::new,
            "Retry failed after " + attempt + " attempt(s)");
    }

    private T handleExhausted(String message, T lastResult, Throwable lastError, int attempts) {
        return handleFailureWithFallback(attempts, lastResult, lastError,
            (ctx, err) -> metrics.exhausted(name, attempts, err),
                RetryEvent.Exhausted::new,
            message);
    }

    private T handleInterrupted(int attempt, InterruptedException ie) {
        return handleFailureWithFallback(attempt, null, ie,
            (ctx, err) -> metrics.attemptFailed(name, attempt, err),
                RetryEvent.AttemptFailed::new,
            "Interrupted during retry");
    }

    private T handleFailureWithFallback(int attempt, T lastResult, Throwable lastError,
                                        BiConsumer<RetryContext<T>, Throwable> metricsFn,
                                        BiFunction<RetryContext<T>, Throwable, RetryEvent<T>> eventFn,
                                        String errorMessage) {
        RetryContext<T> ctxFail = new RetryContext<>(id, attempt, attempt, lastResult, lastError, Duration.ZERO, tags);
        safeRun(() -> listeners.onFailure.accept(lastError, ctxFail));
        safeRun(() -> metricsFn.accept(ctxFail, lastError));

        if (eventBus != null) {
            RetryEvent<T> event = eventFn.apply(ctxFail, lastError);
            safeRun(() -> eventBus.publish(event));
        }

        if (fallback != null) {
            safeRun(() -> listeners.onRecover.accept(ctxFail));
            return fallback.apply(lastError);
        }

        throw new RetryException(errorMessage, lastError, attempt);
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
            return future.get(perAttempt.toMillis(), TimeUnit.MILLISECONDS);
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
