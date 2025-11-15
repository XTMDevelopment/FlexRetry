package id.xtramile.flexretry.backoff;

import java.time.Duration;

/**
 * Fixed delay between attempts.
 */
public final class FixedBackoff implements BackoffStrategy {
    private final Duration delay;

    public FixedBackoff(Duration delay) {
        if (delay == null || delay.isNegative()) {
            throw new IllegalArgumentException("delay must be >= 0");
        }

        this.delay = delay;
    }

    @Override
    public Duration delayForAttempt(int attempt) {
        return delay;
    }

    public static FixedBackoff ofMillis(long millis) {
        return new FixedBackoff(Duration.ofMillis(Math.max(0L, millis)));
    }
}
