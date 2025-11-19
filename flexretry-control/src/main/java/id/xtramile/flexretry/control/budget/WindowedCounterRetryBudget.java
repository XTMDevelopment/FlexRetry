package id.xtramile.flexretry.control.budget;

import java.util.concurrent.TimeUnit;

/**
 * Fixed-window counter: allow up to "limit" retries per window.
 * When a window starts, the counter resets.
 */
public class WindowedCounterRetryBudget implements RetryBudget {
    private final int limit;
    private final long windowNanos;

    private int used = 0;
    private long windowStart = System.nanoTime();
    private final Object lock = new Object();

    public WindowedCounterRetryBudget(int limit, long windowDuration, TimeUnit unit) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit >= 0");
        }

        if (windowDuration <= 0) {
            throw new IllegalArgumentException("windowDuration > 0");
        }

        this.limit = limit;
        this.windowNanos = unit.toNanos(windowDuration);
    }

    @Override
    public boolean tryAcquire() {
        synchronized (lock) {
            rollWindowIfNeeded();

            if (used < limit) {
                used++;
                return true;
            }

            return false;
        }
    }

    private void rollWindowIfNeeded() {
        long now = System.nanoTime();
        if (now - windowStart >= windowNanos) {
            windowStart = now;
            used = 0;
        }
    }

    public int remainingInWindow() {
        synchronized (lock) {
            rollWindowIfNeeded();
            return Math.max(0, limit - used);
        }
    }

    public int limit() {
        return limit;
    }

    public long windowNanos() {
        return windowNanos;
    }
}
