package id.xtramile.flexretry.strategy.backoff;

import java.time.Duration;

/**
 * Wraps a backoff and lets you reset the internal attempt counter curve
 */
public final class ResettableBackoff implements BackoffStrategy {
    private final BackoffStrategy delegate;
    private int streak = 0;

    public ResettableBackoff(BackoffStrategy delegate) {
        this.delegate = delegate;
    }

    public void reset() {
        streak = 0;
    }

    @Override
    public Duration delayForAttempt(int attempt) {
        streak++;
        return delegate.delayForAttempt(streak);
    }
}
