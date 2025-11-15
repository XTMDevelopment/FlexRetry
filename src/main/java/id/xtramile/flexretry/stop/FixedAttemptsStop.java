package id.xtramile.flexretry.stop;

import java.time.Duration;

/**
 * Stop after a fixed number of attempts (attempts are 1-based)
 */
public final class FixedAttemptsStop implements StopStrategy {
    private final int maxAttempts;

    public FixedAttemptsStop(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }

        this.maxAttempts = maxAttempts;
    }

    @Override
    public boolean shouldStop(int attempt, long startNanos, long nowNanos, Duration nextDelay) {
        return attempt > maxAttempts;
    }

    public int maxAttempts() {
        return maxAttempts;
    }
}
