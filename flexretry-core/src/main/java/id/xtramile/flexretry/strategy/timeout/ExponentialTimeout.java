package id.xtramile.flexretry.strategy.timeout;

import java.time.Duration;

public final class ExponentialTimeout implements AttemptTimeoutStrategy {
    private final Duration initial;
    private final double multiplier;
    private final Duration cap;

    public ExponentialTimeout(Duration initial, double multiplier, Duration cap) {
        if (initial == null || initial.isNegative()) {
            throw new IllegalArgumentException("initial must be >= 0");
        }

        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0");
        }

        if (cap == null || cap.isNegative()) {
            throw new IllegalArgumentException("cap must be >= 0");
        }

        this.initial = initial;
        this.multiplier = multiplier;
        this.cap = cap;
    }

    public static AttemptTimeoutStrategy ofMillis(long initialMillis, double multiplier, long capMillis) {
        return new ExponentialTimeout(Duration.ofMillis(initialMillis), multiplier, Duration.ofMillis(capMillis));
    }

    @Override
    public Duration timeoutForAttempt(int attempt) {
        int n = Math.max(0, attempt - 1);
        double factor = Math.pow(multiplier, n);

        long ms = Math.round(initial.toMillis() * factor);
        if (ms < 0) {
            ms = Long.MAX_VALUE;
        }

        long capMs = cap.toMillis();
        if (capMs > 0 && ms > capMs) {
            ms = capMs;
        }

        return Duration.ofMillis(ms);
    }
}
