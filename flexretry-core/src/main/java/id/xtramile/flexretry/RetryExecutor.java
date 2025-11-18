package id.xtramile.flexretry;

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
import java.util.concurrent.*;
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

    // ---- Timeouts / executor ----
    private final Duration attemptTimeout;
    private final ExecutorService attemptExecutor;
    private final AttemptTimeoutStrategy attemptTimeouts;

    // ---- Task / fallback ----
    private final Callable<T> task;
    private final Function<Throwable, T> fallback;

    // ---- Lifecycle ----
    private final AttemptLifecycle<T> lifecycle;


    public RetryExecutor(
            // identity
            String name, String id, Map<String, Object> tags,
            // timing/stop/backoff
            StopStrategy stop, BackoffStrategy backoff,
            // policy
            RetryPolicy<T> policy,
            // infra
            RetryListeners<T> listeners, Sleeper sleeper, Clock clock,
            // timeouts / executor
            Duration attemptTimeout, ExecutorService attemptExecutor,
            // task + fallback
            Callable<T> task, Function<Throwable, T> fallback,
            // advanced params
            BackoffRouter backoffRouter,
            AttemptLifecycle<T> lifecycle,
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

        // timeouts/executor
        this.attemptTimeout = attemptTimeout; // may be null
        this.attemptExecutor = attemptExecutor; // may be null
        this.attemptTimeouts = attemptTimeouts; // may be null

        // task/fallback
        this.task = Objects.requireNonNull(task, "task");
        this.fallback = fallback; // may be null

        // lifecycle
        this.lifecycle = lifecycle;
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
        } catch (Throwable ignore) {
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

                announceAttempt(ctxBefore);

                try {
                    T result = executeAttempt(attempt);
                    lastResult = result;
                    lastError = null;

                    afterAttemptSuccess(ctxBefore, result);

                    if (policy.shouldRetry(result, null, attempt, Integer.MAX_VALUE)) {
                        sleepAdjusted(nextDelay, ctxBefore);
                        continue;
                    }

                    finalAttempt = attempt;
                    return handleSuccess(attempt, result);

                } catch (Throwable e) {
                    lastError = unwrap(e);
                    afterAttemptFailure(ctxBefore, lastError);

                    if (policy.shouldRetry(null, lastError, attempt, Integer.MAX_VALUE)) {
                        sleepAdjusted(nextDelay, ctxBefore);
                        continue;
                    }

                    finalAttempt = attempt;
                    return finalizeFailure(attempt, lastResult, lastError);
                }
            }

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();

            int att = finalAttempt == 0 ? 1 : finalAttempt;
            return handleInterrupted(att, ie);

        } finally {
            int att = finalAttempt == 0 ? 1 : finalAttempt;
            safeRun(() -> listeners.onFinally.accept(new RetryContext<>(id, name, att, att, null, null, Duration.ZERO, tags)));
        }
    }

    private Duration computeNextDelay(int attempt, Throwable lastError) {
        if (lastError != null && backoffRouter != null) {
            return backoffRouter.select(lastError).delayForAttempt(attempt);
        }

        return backoff.delayForAttempt(attempt);
    }

    private RetryContext<T> buildContext(int attempt, T lastResult, Throwable lastError, Duration nextDelay) {
        return new RetryContext<>(id, name, attempt, Integer.MAX_VALUE, lastResult, lastError, nextDelay, tags);
    }

    private boolean shouldStopBeforeAttempt(int attempt, long startNanos, Duration nextDelay) {
        if (attempt <= 1) {
            return false;
        }

        long now = clock.nanoTime();
        return stop.shouldStop(attempt, startNanos, now, nextDelay);
    }

    private void announceAttempt(RetryContext<T> ctxBefore) {
        safeRun(() -> listeners.onAttempt.accept(ctxBefore));

        if (lifecycle != null) {
            safeRun(() -> lifecycle.beforeAttempt(ctxBefore));
        }
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

    private void sleepAdjusted(Duration proposed, RetryContext<T> ctx) throws InterruptedException {
        Duration adjusted = nullSafe(() -> listeners.beforeSleep.apply(proposed, ctx), proposed);
        sleeper.sleep(adjusted);
    }

    private T handleSuccess(int attempt, T result) {
        RetryContext<T> ctxSuccess = new RetryContext<>(id, name, attempt, attempt, result, null, Duration.ZERO, tags);
        safeRun(() -> listeners.onSuccess.accept(result, ctxSuccess));

        if (lifecycle != null) {
            safeRun(() -> lifecycle.afterSuccess(ctxSuccess));
        }

        return result;
    }

    private T finalizeFailure(int attempt, T lastResult, Throwable lastError) {
        return handleFailureWithFallback(attempt, lastResult, lastError,
                "Retry failed after " + attempt + " attempt(s)");
    }

    private T handleExhausted(String message, T lastResult, Throwable lastError, int attempts) {
        return handleFailureWithFallback(attempts, lastResult, lastError,
                message);
    }

    private T handleInterrupted(int attempt, InterruptedException ie) {
        return handleFailureWithFallback(attempt, null, ie,
                "Interrupted during retry");
    }

    private T handleFailureWithFallback(int attempt, T lastResult, Throwable lastError, String errorMessage) {
        RetryContext<T> ctxFail = new RetryContext<>(id, name, attempt, attempt, lastResult, lastError, Duration.ZERO, tags);
        safeRun(() -> listeners.onFailure.accept(lastError, ctxFail));

        if (fallback != null) {
            safeRun(() -> listeners.onRecover.accept(ctxFail));
            return fallback.apply(lastError);
        }

        throw new RetryException(errorMessage, lastError, attempt);
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

    @FunctionalInterface
    private interface SupplierWithException<R> {
        R get() throws Exception;
    }
}
