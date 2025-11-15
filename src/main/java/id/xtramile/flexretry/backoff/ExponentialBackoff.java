package id.xtramile.flexretry.backoff;

import java.time.Duration;

/**
 * Exponential backoff: initialDelay * multiplier ^ (attempt - 1)
 */
public final class ExponentialBackoff implements BackoffStrategy {
    private final Duration initial;
    private final double multiplier;

    public ExponentialBackoff(Duration initial, double multiplier) {
        if (initial == null || initial.isNegative()) {
            throw new IllegalArgumentException("initial must be >= 0");
        }

        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0");
        }

        this.initial = initial;
        this.multiplier = multiplier;
    }

    @Override
    public Duration delayForAttempt(int attempt) {
        if (attempt <= 1) {
            return initial;
        }

        double factor = Math.pow(multiplier, attempt - 1);
        long ms = Math.round(initial.toMillis() * factor);

        if (ms < 0) {
            ms = Long.MAX_VALUE;
        }

        return Duration.ofMillis(ms);
    }

    public static ExponentialBackoff ofMillis(long initialMillis, double multiplier) {
        return new ExponentialBackoff(Duration.ofMillis(initialMillis), multiplier);
    }
}
