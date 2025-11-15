package id.xtramile.flexretry;

import id.xtramile.flexretry.policy.RetryPolicy;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Runs the attempt loop using a policy + backoff + listeners
 */
public final class RetryExecutor<T> {
    private final int maxAttempts;
    private final BackoffStrategy backoff;
    private final RetryPolicy<T> policy;
    private final RetryListeners<T> listeners;
    private final Sleeper sleeper;

    public RetryExecutor(int maxAttempts, BackoffStrategy backoff, RetryPolicy<T> policy, RetryListeners<T> listeners, Sleeper sleeper) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }

        this.maxAttempts = maxAttempts;
        this.backoff = Objects.requireNonNull(backoff, "backoff");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.listeners = Objects.requireNonNullElseGet(listeners, RetryListeners::new);
        this.sleeper = Objects.requireNonNullElseGet(sleeper, Sleeper::system);
    }

    public T run(Callable<T> task) {
        Objects.requireNonNull(task, "task");

        T lastResult = null;
        Throwable lastError = null;
        int finalAttempt = 0;

        try {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                Duration nextDelay = (attempt < maxAttempts) ? backoff.delayForAttempt(attempt) : Duration.ZERO;
                RetryContext<T> ctxBefore = new RetryContext<>(attempt, maxAttempts, lastResult, lastError, nextDelay);

                safeRun(() -> listeners.onAttempt.accept(ctxBefore));

                try {
                    T result = task.call();
                    lastResult = result;
                    lastError = null;

                    boolean shouldRetry = policy.shouldRetry(result, null, attempt, maxAttempts);
                    if (shouldRetry) {
                        if (attempt < maxAttempts) {
                            sleep(nextDelay);
                            continue;

                        } else {
                            finalAttempt = attempt;
                            safeRun(() -> listeners.onFailure.accept(null,
                                    new RetryContext<>(attempt, maxAttempts, result, null, Duration.ZERO)));

                            throw new RetryException("Retry exhausted: result did not meet success condition", null, attempt);
                        }
                    }

                    finalAttempt = attempt;
                    safeRun(() -> listeners.onSuccess.accept(result,
                            new RetryContext<>(attempt, maxAttempts, result, null, Duration.ZERO)));

                    return result;

                } catch (Throwable e) {
                    lastError = unwrap(e);

                    boolean shouldRetry = policy.shouldRetry(null, lastError, attempt, maxAttempts);
                    if (shouldRetry && attempt < maxAttempts) {
                        sleep(nextDelay);
                        continue;
                    }

                    finalAttempt = attempt;
                    Throwable finalErr = lastError;
                    safeRun(() -> listeners.onFailure.accept(finalErr,
                            new RetryContext<>(attempt, maxAttempts, lastResult, finalErr, Duration.ZERO)));

                    throw new RetryException("Retry failed after " + attempt + " attempt(s)", finalErr, attempt);
                }
            }

            throw new IllegalStateException("Unexpected retry state");

        } finally {
            int att = (finalAttempt == 0) ? 1 : finalAttempt;
            safeRun(() -> listeners.onFinally.accept(new RetryContext<>(att, maxAttempts, lastResult, lastError, Duration.ZERO)));
        }
    }

    private static void sleep(Duration duration) {
        try {
            Sleeper.system().sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during backoff sleep", e);
        }
    }

    private static void safeRun(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable ignore) {}
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof RuntimeException && throwable.getCause() != null) {
            return throwable.getCause();
        }

        return throwable;
    }
}
