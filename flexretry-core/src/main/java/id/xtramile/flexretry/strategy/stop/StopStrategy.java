package id.xtramile.flexretry.strategy.stop;

import java.time.Duration;

public interface StopStrategy {

    static StopStrategy maxAttempts(int maxAttempts) {
        return new FixedAttemptsStop(maxAttempts);
    }

    static StopStrategy maxElapsed(Duration budget) {
        return new MaxElapsedStop(budget);
    }

    static StopStrategy compose(StopStrategy... stopStrategies) {
        return new CompositeStop(stopStrategies);
    }

    boolean shouldStop(int attempt, long startNanos, long nowNanos, Duration nextDelay);
}
