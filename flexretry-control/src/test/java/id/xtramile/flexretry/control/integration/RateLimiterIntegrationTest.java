package id.xtramile.flexretry.control.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.RetryException;
import id.xtramile.flexretry.control.RetryControls;
import id.xtramile.flexretry.control.ratelimit.RateLimitExceededException;
import id.xtramile.flexretry.control.ratelimit.RateLimiter;
import id.xtramile.flexretry.control.ratelimit.TokenBucketRateLimiter;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RateLimiter with RetryExecutors.
 * Demonstrates custom RateLimiter implementations.
 */
class RateLimiterIntegrationTest {

    /**
     * Custom RateLimiter that allows a fixed number of requests per time window
     */
    static class FixedWindowRateLimiter implements RateLimiter {
        private final int maxRequests;
        private final AtomicInteger requests = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();
        private final long windowMs;

        FixedWindowRateLimiter(int maxRequests, long windowMs) {
            this.maxRequests = maxRequests;
            this.windowMs = windowMs;
        }

        @Override
        public synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - windowStart >= windowMs) {
                requests.set(0);
                windowStart = now;
            }

            if (requests.get() < maxRequests) {
                requests.incrementAndGet();
                return true;
            }

            return false;
        }
    }

    /**
     * Custom RateLimiter with priority levels
     */
    static class PriorityRateLimiter implements RateLimiter {
        private final int highPriorityLimit;
        private final int lowPriorityLimit;
        private final AtomicInteger highPriorityUsed = new AtomicInteger(0);
        private final AtomicInteger lowPriorityUsed = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();
        private final long windowMs;

        PriorityRateLimiter(int highPriorityLimit, int lowPriorityLimit, long windowMs) {
            this.highPriorityLimit = highPriorityLimit;
            this.lowPriorityLimit = lowPriorityLimit;
            this.windowMs = windowMs;
        }

        @Override
        public synchronized boolean tryAcquire() {
            return tryAcquire(false);
        }

        public synchronized boolean tryAcquire(boolean highPriority) {
            long now = System.currentTimeMillis();
            if (now - windowStart >= windowMs) {
                highPriorityUsed.set(0);
                lowPriorityUsed.set(0);
                windowStart = now;
            }

            if (highPriority) {
                if (highPriorityUsed.get() < highPriorityLimit) {
                    highPriorityUsed.incrementAndGet();
                    return true;
                }

            } else {
                if (lowPriorityUsed.get() < lowPriorityLimit) {
                    lowPriorityUsed.incrementAndGet();
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * Custom RateLimiter that allows burst with refill
     */
    static class BurstRateLimiter implements RateLimiter {
        private final int capacity;
        private final double refillRate;
        private double tokens;
        private volatile long lastRefill = System.currentTimeMillis();

        BurstRateLimiter(int capacity, double refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.tokens = capacity;
        }

        @Override
        public synchronized boolean tryAcquire() {
            refill();

            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }

            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefill;

            if (elapsed > 0) {
                double tokensToAdd = (elapsed / 1000.0) * refillRate;
                tokens = Math.min(capacity, tokens + tokensToAdd);
                lastRefill = now;
            }
        }
    }

    @Test
    void testRetryWithTokenBucketRateLimiter() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 10);
        AtomicInteger attemptCount = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
            .maxAttempts(3)
            .execute(RetryControls.rateLimited(() -> {
                attemptCount.incrementAndGet();
                return "success";
            }, limiter))
            .getResult();

        assertEquals("success", result);
        assertEquals(1, attemptCount.get());
    }

    @Test
    void testRetryWithTokenBucketRateLimiter_Exceeded() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1);
        limiter.tryAcquire();

        RetryException exception = assertThrows(RetryException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(3)
                    .execute(RetryControls.rateLimited(() -> "success", limiter))
                    .getResult());

        assertInstanceOf(RateLimitExceededException.class, exception.getCause());
    }

    @Test
    void testRetryWithCustomFixedWindowRateLimiter() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(5, 1000);
        AtomicInteger attemptCount = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            final int index = i;
            String result = Retry.<String>newBuilder()
                .maxAttempts(1)
                .execute(RetryControls.rateLimited(() -> {
                    attemptCount.incrementAndGet();
                    return "success" + index;
                }, limiter))
                .getResult();

            assertEquals("success" + i, result);
        }

        assertEquals(5, attemptCount.get());

        RetryException exception = assertThrows(RetryException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(1)
                    .execute(RetryControls.rateLimited(() -> "success6", limiter))
                    .getResult());

        assertInstanceOf(RateLimitExceededException.class, exception.getCause());
    }

    @Test
    void testRetryWithCustomFixedWindowRateLimiter_WindowReset() throws Exception {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(2, 100);
        AtomicInteger attemptCount = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            Retry.<String>newBuilder()
                .maxAttempts(1)
                .execute(RetryControls.rateLimited(() -> {
                    attemptCount.incrementAndGet();
                    return "success";
                }, limiter))
                .getResult();
        }

        assertEquals(2, attemptCount.get());

        assertThrows(RetryException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(1)
                    .execute(RetryControls.rateLimited(() -> "success", limiter))
                    .getResult());

        Thread.sleep(150);

        String result = Retry.<String>newBuilder()
            .maxAttempts(1)
            .execute(RetryControls.rateLimited(() -> {
                attemptCount.incrementAndGet();
                return "success";
            }, limiter))
            .getResult();

        assertEquals("success", result);
        assertEquals(3, attemptCount.get());
    }

    @Test
    void testRetryWithCustomBurstRateLimiter() {
        BurstRateLimiter limiter = new BurstRateLimiter(5, 2.0);
        AtomicInteger attemptCount = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            final int index = i;
            String result = Retry.<String>newBuilder()
                .maxAttempts(1)
                .execute(RetryControls.rateLimited(() -> {
                    attemptCount.incrementAndGet();
                    return "success" + index;
                }, limiter))
                .getResult();

            assertEquals("success" + i, result);
        }

        assertEquals(5, attemptCount.get());

        RetryException exception = assertThrows(RetryException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(1)
                    .execute(RetryControls.rateLimited(() -> "success6", limiter))
                    .getResult());

        assertInstanceOf(RateLimitExceededException.class, exception.getCause());
    }
}

