package id.xtramile.flexretry.strategy.backoff;

import id.xtramile.flexretry.support.rand.RandomSource;

import java.time.Duration;

public final class JitterDecorator implements BackoffStrategy {
    private final BackoffStrategy base;
    private final double fraction;
    private final RandomSource rnd;

    public JitterDecorator(BackoffStrategy base, double fraction, RandomSource rnd) {
        if (fraction < 0 || fraction > 1) {
            throw new IllegalArgumentException("fraction must be between 0 and 1");
        }

        this.base = base;
        this.fraction = fraction;
        this.rnd = rnd == null ? RandomSource.threadLocal() : rnd;
    }

    @Override
    public Duration delayForAttempt(int attempt) {
        Duration duration = base.delayForAttempt(attempt);

        long ms = Math.max(0, duration.toMillis());
        long jitter = (long) (ms * fraction);
        long low = Math.max(0, ms - jitter);
        long high = ms + jitter + 1;
        long pick = rnd.nextLong(low, Math.max(low + 1, high));

        return Duration.ofMillis(pick);
    }
}
