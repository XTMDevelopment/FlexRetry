package id.xtramile.flexretry.control.ratelimit;

import java.util.concurrent.atomic.AtomicLong;

public final class FixedWindowRateLimiter implements RateLimiter {
    private final long limitPerWindow;
    private final long windowNanos;
    private final AtomicLong counter = new AtomicLong(0);
    private volatile long windowStart = System.nanoTime();

    public FixedWindowRateLimiter(long limitPerWindow, long windowMillis) {
        if (limitPerWindow < 1 || windowMillis < 1) {
            throw new IllegalArgumentException("limitPerWindow and windowMillis >= 1");
        }

        this.limitPerWindow = limitPerWindow;
        this.windowNanos = windowMillis * 1_000_000L;
    }

    @Override
    public synchronized boolean tryAcquire() {
        long now = System.nanoTime();

        if (now - windowStart >= windowNanos) {
            windowStart = now;
            counter.set(0);
        }

        long current = counter.incrementAndGet();
        return current <= limitPerWindow;
    }
}
