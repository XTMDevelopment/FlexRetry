package id.xtramile.flexretry.strategy.timeout;

import java.time.Duration;

public interface AttemptTimeoutStrategy {

    static AttemptTimeoutStrategy fixed(Duration duration) {
        return new FixedTimeout(duration);
    }

    static AttemptTimeoutStrategy exponential(Duration initial, double multiplier, Duration cap) {
        return new ExponentialTimeout(initial, multiplier, cap);
    }

    Duration timeoutForAttempt(int attempt);
}
