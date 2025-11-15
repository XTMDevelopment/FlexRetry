package id.xtramile.flexretry.backoff;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AWS "decorrelated jitter": next = min(cap, rand(base, prev * 3))
 */
public class DecorrelatedJitterBackoff implements BackoffStrategy {
    private final long baseMs;
    private final long capMs;
    private long prevMs;

    public DecorrelatedJitterBackoff(Duration base, Duration cap) {
        if (base == null || base.isNegative()) {
            throw new IllegalArgumentException("base >= 0");
        }

        if (cap == null || cap.isNegative()) {
            throw new IllegalArgumentException("cap >= 0");
        }

        this.baseMs = base.toMillis();
        this.capMs = cap.toMillis();
        this.prevMs = baseMs;
    }

    @Override
    public Duration delayForAttempt(int attempt) {
        long next = Math.min(capMs, ThreadLocalRandom.current().nextLong(baseMs, Math.max(baseMs + 1, prevMs * 3)));
        prevMs = next;
        return Duration.ofMillis(next);
    }
}
