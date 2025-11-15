package id.xtramile.flexretry.timeouts;

import java.time.Duration;

public interface AttemptTimeoutStrategy {
    Duration timeoutForAttempt(int attempt);

    static AttemptTimeoutStrategy fixed(Duration duration) {
        return attempt -> duration;
    }

    static AttemptTimeoutStrategy exponential(Duration initial, double multiplier, Duration cap) {
        return attempt -> {
            long ms = Math.round(initial.toMillis() * Math.pow(multiplier, Math.max(0, attempt - 1)));
            Duration duration = Duration.ofMillis(ms);

            return duration.compareTo(cap) > 0 ? cap : duration;
        };
    }
}
