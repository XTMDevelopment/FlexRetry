package id.xtramile.flexretry.backoff;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Computes the delay before the next attempts. Attempt number start from 1.
 */
public interface BackoffStrategy {
    Duration delayForAttempt(int attempt);

    /**
     * Wrap this strategy with jitter, +/- (fraction * baseDelay).
     */
    default BackoffStrategy withJitter(double fraction) {
        if (fraction < 0 || fraction > 1) {
            throw new IllegalArgumentException("jitter fraction must be between 0 and 1");
        }

        BackoffStrategy delegate = this;

        return attempt -> {
            Duration base = delegate.delayForAttempt(attempt);

            long ms = Math.max(0L, base.toMillis());
            long jitter = (long) (ms * fraction);
            long low = Math.max(0L, ms - jitter);
            long high = ms + jitter + 1;

            long randomized = ThreadLocalRandom.current().nextLong(low, Math.max(low + 1, high));
            return Duration.ofMillis(randomized);
        };
    }

    static BackoffStrategy fixed(Duration delay) {
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must be >= 0");
        }

        return attempt -> delay;
    }

    static BackoffStrategy exponential(Duration initial, double multiplier) {
        if (initial.isNegative()) {
            throw new IllegalArgumentException("initial must be >= 0");
        }

        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0");
        }

        return attempt -> {
            if (attempt <= 1) {
                return initial;
            }

            double factor = Math.pow(multiplier, attempt - 1);
            long ms = Math.round(initial.toMillis() * factor);

            if (ms < 0) {
                ms = Long.MAX_VALUE;
            }

            return Duration.ofMillis(ms);
        };
    }
}
