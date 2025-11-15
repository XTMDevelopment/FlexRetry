package id.xtramile.flexretry.stop;

import java.time.Duration;

public interface StopStrategy {
    boolean shouldStop(int attempt, long startNanos, long nowNanos, Duration nextDelay);
}
