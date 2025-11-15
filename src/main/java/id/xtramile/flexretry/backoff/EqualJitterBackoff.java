package id.xtramile.flexretry.backoff;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AWS "equal jitter": delay = half + rand(0..half) around exponential base.
 */
public final class EqualJitterBackoff implements BackoffStrategy {
    private final long baseMs;
    private final double multiplier;

    public EqualJitterBackoff(Duration base, double multiplier) {
        if (base == null || base.isNegative()) {
            throw new IllegalArgumentException("base >= 0");
        }

        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier >= 1.0");
        }

        this.baseMs = base.toMillis();
        this.multiplier = multiplier;
    }

    @Override
    public Duration delayForAttempt(int attempt) {
        long exp = Math.round(baseMs * Math.pow(multiplier, Math.max(0, attempt - 1)));
        long half = exp / 2;
        long rand = ThreadLocalRandom.current().nextLong(0, half + 1);

        return Duration.ofMillis(half + rand);
    }
}
