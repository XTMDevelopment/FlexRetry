package id.xtramile.flexretry.strategy.stop;

import java.time.Duration;

/**
 * Stop when the elapsed time budget would be exceeded if we slept nextDelay
 */
public final class MaxElapsedStop implements StopStrategy {
    private final long budgetNanos;

    public MaxElapsedStop(Duration budget) {
        if (budget == null || budget.isNegative()) {
            throw new IllegalArgumentException("budget must be >= 0");
        }

        this.budgetNanos = budget.toNanos();
    }

    @Override
    public boolean shouldStop(int attempt, long startNanos, long nowNanos, Duration nextDelay) {
        long elapsed = nowNanos - startNanos;
        long next = Math.max(0L, nextDelay == null ? 0L : nextDelay.toNanos());

        return elapsed + next > budgetNanos;
    }

    public Duration budget() {
        return Duration.ofNanos(budgetNanos);
    }
}
