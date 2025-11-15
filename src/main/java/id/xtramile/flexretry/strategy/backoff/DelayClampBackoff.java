package id.xtramile.flexretry.strategy.backoff;

import java.time.Duration;

public final class DelayClampBackoff implements BackoffStrategy {
    private final BackoffStrategy base;
    private final Duration min;
    private final Duration max;

    public DelayClampBackoff(BackoffStrategy base, Duration min, Duration max) {
        this.base = base;
        this.min = min == null ? Duration.ZERO : min;
        this.max = max == null ? Duration.ofDays(365) : max;
    }

    @Override
    public Duration delayForAttempt(int attempt) {
        Duration duration = base.delayForAttempt(attempt);

        if (duration.compareTo(min) < 0) {
            return min;
        }

        if (duration.compareTo(max) > 0) {
            return max;
        }

        return duration;
    }
}
