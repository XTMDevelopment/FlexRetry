package id.xtramile.flexretry.strategy.stop;

import java.time.Duration;
import java.util.Optional;

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

    @Override
    public Optional<Integer> maxAttempts() {
        return Optional.of(maxAttempts);
    }
}
