package id.xtramile.flexretry.strategy.stop;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Stop if ANY child strategy says to stop
 */
public final class CompositeStop implements StopStrategy {
    private final List<StopStrategy> list;

    public CompositeStop(StopStrategy... strategies) {
        this.list = Arrays.asList(strategies);
    }

    @Override
    public boolean shouldStop(int attempt, long startNanos, long nowNanos, Duration nextDelay) {
        for (StopStrategy strategy : list) {
            if (strategy != null && strategy.shouldStop(attempt, startNanos, nowNanos, nextDelay)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Optional<Integer> maxAttempts() {
        return list.stream()
                .filter(strategy -> strategy != null)
                .map(StopStrategy::maxAttempts)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .min(Integer::compareTo);
    }
}
