package id.xtramile.flexretry.strategy.backoff;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Computes the delay before the next attempts. Attempt number start from 1.
 */
public interface BackoffStrategy {

    static BackoffStrategy fixed(Duration delay) {
        return new FixedBackoff(delay);
    }

    static BackoffStrategy exponential(Duration initial, double multiplier) {
        return new ExponentialBackoff(initial, multiplier);
    }

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
}
