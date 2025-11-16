package id.xtramile.flexretry.control.budget;

import java.util.concurrent.TimeUnit;

/**
 * Simple token-bucket budget shared across callers.
 * Refill rate: "tokensPerSecond" (can be fractional)
 * Burst capacity: "capacity"
 */
public final class TokenBucketRetryBudget implements RetryBudget {
    private final double tokensPerSecond;
    private final double capacity;

    private double tokens;
    private long lastRefillNanoTime;

    public TokenBucketRetryBudget(double tokensPerSecond, double capacity) {
        if (tokensPerSecond < 0) {
            throw new IllegalArgumentException("tokensPerSecond >= 0");
        }

        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity > 0");
        }

        this.tokensPerSecond = tokensPerSecond;
        this.capacity = capacity;
        this.tokens = capacity;
        this.lastRefillNanoTime = System.nanoTime();
    }

    @Override
    public boolean tryAcquire() {
        refill();

        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }

        return false;
    }

    private void refill() {
        long now = System.nanoTime();
        long deltaNanos = now - lastRefillNanoTime;

        if (deltaNanos <= 0) {
            return;
        }

        double deltaSeconds = deltaNanos / (double) TimeUnit.SECONDS.toNanos(1);
        double added = deltaSeconds * tokensPerSecond;
        tokens = Math.min(capacity, tokens + added);
        lastRefillNanoTime = now;
    }

    public synchronized double availableTokens() {
        return tokens;
    }

    public double capacity() {
        return capacity;
    }

    public double ratePerSecond() {
        return tokensPerSecond;
    }
}
