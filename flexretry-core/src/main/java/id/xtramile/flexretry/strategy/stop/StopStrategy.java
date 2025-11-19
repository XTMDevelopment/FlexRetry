package id.xtramile.flexretry.strategy.stop;

import java.time.Duration;
import java.util.Optional;

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

    /**
     * Returns the maximum number of attempts if this strategy has a fixed limit.
     * Returns empty if this strategy doesn't have a fixed attempt limit
     * (e.g., time-based strategies or custom implementations).
     *
     * @return Optional containing maxAttempts if available, empty otherwise
     */
    default Optional<Integer> maxAttempts() {
        return Optional.empty();
    }
}
