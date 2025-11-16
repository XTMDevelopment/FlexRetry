package id.xtramile.flexretry.strategy.timeout;

import java.time.Duration;

public final class FixedTimeout implements AttemptTimeoutStrategy {
    private final Duration duration;

    public FixedTimeout(Duration duration) {
        if (duration == null || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be >= 0");
        }

        this.duration = duration;
    }

    public static AttemptTimeoutStrategy ofMillis(long millis) {
        return new FixedTimeout(Duration.ofMillis(Math.max(0L, millis)));
    }

    @Override
    public Duration timeoutForAttempt(int attempt) {
        return duration;
    }
}
