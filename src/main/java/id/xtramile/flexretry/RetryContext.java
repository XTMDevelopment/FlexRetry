package id.xtramile.flexretry;

import java.time.Duration;

/**
 * Immutable snapshot passed to callbacks.
 */
public final class RetryContext<T> {
    private final int attempt;
    private final int maxAttempts;
    private final T lastResult;
    private final Throwable lastError;
    private final Duration nextDelay;

    public RetryContext(int attempt, int maxAttempts, T lastResult, Throwable lastError, Duration nextDelay) {
        this.attempt = attempt;
        this.maxAttempts = maxAttempts;
        this.lastResult = lastResult;
        this.lastError = lastError;
        this.nextDelay = nextDelay == null ? Duration.ZERO : nextDelay;
    }

    public int attempt() {
        return attempt;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public T lastResult() {
        return lastResult;
    }

    public Throwable lastError() {
        return lastError;
    }

    public Duration nextDelay() {
        return nextDelay;
    }
}
