package id.xtramile.flexretry.observability;

import id.xtramile.flexretry.RetryContext;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Helper class to track timing for metrics.
 */
final class TimingTracker<T> {
    private final Map<String, AtomicLong> attemptStarts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> totalStarts = new ConcurrentHashMap<>();

    void onAttempt(RetryContext<T> ctx) {
        String key = ctx.id();

        attemptStarts.put(key, new AtomicLong(System.nanoTime()));

        if (ctx.attempt() == 1) {
            totalStarts.put(key, new AtomicLong(System.nanoTime()));
        }
    }

    Duration getElapsed(RetryContext<T> ctx) {
        AtomicLong start = attemptStarts.get(ctx.id());

        if (start != null) {
            long elapsedNanos = System.nanoTime() - start.get();
            return Duration.ofNanos(elapsedNanos);
        }

        return Duration.ZERO;
    }

    Duration getTotalElapsed(RetryContext<T> ctx) {
        AtomicLong start = totalStarts.get(ctx.id());

        if (start != null) {
            long elapsedNanos = System.nanoTime() - start.get();
            return Duration.ofNanos(elapsedNanos);
        }

        return Duration.ZERO;
    }
}

