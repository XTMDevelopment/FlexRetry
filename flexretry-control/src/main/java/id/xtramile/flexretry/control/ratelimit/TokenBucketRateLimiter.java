package id.xtramile.flexretry.control.ratelimit;

import java.util.concurrent.atomic.AtomicLong;

public final class TokenBucketRateLimiter implements RateLimiter {
    private final long capacity;
    private final long refillPerNanos;
    private final AtomicLong tokens = new AtomicLong();
    private volatile long lastRefillNanos = System.nanoTime();

    public TokenBucketRateLimiter(long capacity, long refillPerSecond) {
        if (capacity < 1 || refillPerSecond < 1) {
            throw new IllegalArgumentException("capacity and refillPerSecond >= 1");
        }

        this.capacity = capacity;
        this.refillPerNanos = Math.max(1, 1_000_000_000L / refillPerSecond);
        this.tokens.set(capacity);
    }

    @Override
    public boolean tryAcquire() {
        refill();
        long token;

        do {
            token = tokens.get();
            if (token <= 0) {
                return false;
            }

        } while (!tokens.compareAndSet(token, token - 1));

        return true;
    }

    public void refill() {
        long now = System.nanoTime();
        long elapsed = now - lastRefillNanos;

        if (elapsed < refillPerNanos) {
            return;
        }

        long add = Math.min(capacity, elapsed / refillPerNanos);
        if (add <= 0) {
            return;
        }

        tokens.updateAndGet(curr -> Math.min(capacity, curr + add));
        lastRefillNanos = now;
    }
}
