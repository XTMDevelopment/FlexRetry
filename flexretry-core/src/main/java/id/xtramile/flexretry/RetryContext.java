package id.xtramile.flexretry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable snapshot passed to callbacks.
 */
public final class RetryContext<T> {
    private final String id;
    private final int attempt;
    private final int maxAttempts;
    private final T lastResult;
    private final Throwable lastError;
    private final Duration nextDelay;
    private final Map<String, Object> tags;

    public RetryContext(
            String id,
            int attempt,
            int maxAttempts,
            T lastResult,
            Throwable lastError,
            Duration nextDelay,
            Map<String, Object> tags
    ) {
        this.id = id;
        this.attempt = attempt;
        this.maxAttempts = maxAttempts;
        this.lastResult = lastResult;
        this.lastError = lastError;
        this.nextDelay = nextDelay == null ? Duration.ZERO : nextDelay;
        this.tags = tags == null ? new HashMap<>() : tags;
    }

    public String id() {
        return id;
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

    public Map<String, Object> tags() {
        return tags;
    }
}
