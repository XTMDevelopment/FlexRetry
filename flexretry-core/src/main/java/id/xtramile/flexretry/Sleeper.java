package id.xtramile.flexretry;

import java.time.Duration;

/**
 * Abstraction for sleeping; makes tests deterministic
 */
@FunctionalInterface
public interface Sleeper {
    static Sleeper system() {
        return duration -> {
            long ms = Math.max(0L, duration.toMillis());
            if (ms > 0) {
                Thread.sleep(ms);
            }
        };
    }

    void sleep(Duration duration) throws InterruptedException;
}
