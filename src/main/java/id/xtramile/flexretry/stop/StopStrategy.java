package id.xtramile.flexretry.stop;

import java.time.Duration;

public interface StopStrategy {
    boolean shouldStop(int attempt, long startNanos, long nowNanos, Duration nextDelay);

    static StopStrategy maxAttempts(int attempts) {
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be >= 1");
        }

        return ((attempt, start, now, next) -> attempt >= attempts);
    }

    static StopStrategy maxElapsed(Duration budget) {
        if (budget == null || budget.isNegative()) {
            throw new IllegalArgumentException("budget must be >= 0");
        }

        final long budgetNanos = budget.toNanos();
        return ((attempt, start, now, next) -> (now - start) + Math.max(0, next.toNanos()) > budgetNanos);
    }

    static StopStrategy compose(StopStrategy... strategies) {
        return (attempt, start, now, next) -> {
            for (StopStrategy strategy : strategies) {
                if (strategy != null && strategy.shouldStop(attempt, start, now, next)) {
                    return true;
                }
            }
            return false;
        };
    }
}
