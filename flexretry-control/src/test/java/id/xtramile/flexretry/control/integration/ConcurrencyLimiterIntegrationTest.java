package id.xtramile.flexretry.control.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.RetryException;
import id.xtramile.flexretry.control.RetryControls;
import id.xtramile.flexretry.control.concurrency.AimdConcurrencyLimiter;
import id.xtramile.flexretry.control.concurrency.ConcurrencyLimitedException;
import id.xtramile.flexretry.control.concurrency.ConcurrencyLimiter;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ConcurrencyLimiter with RetryExecutors.
 * Demonstrates custom ConcurrencyLimiter implementations.
 */
class ConcurrencyLimiterIntegrationTest {

    /**
     * Custom ConcurrencyLimiter with fixed limit
     */
    static class FixedConcurrencyLimiter implements ConcurrencyLimiter {
        private final int maxConcurrency;
        private final AtomicInteger current = new AtomicInteger(0);

        FixedConcurrencyLimiter(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
        }

        @Override
        public boolean tryAcquire() {
            int currentValue = current.get();
            if (currentValue < maxConcurrency) {
                return current.compareAndSet(currentValue, currentValue + 1);
            }
            return false;
        }

        @Override
        public void onSuccess() {
            current.decrementAndGet();
        }

        @Override
        public void onDropped() {
            current.decrementAndGet();
        }
    }

    /**
     * Custom ConcurrencyLimiter with adaptive limit based on success rate
     */
    static class AdaptiveConcurrencyLimiter implements ConcurrencyLimiter {
        private int limit;
        private final int minLimit;
        private final int maxLimit;
        private final AtomicInteger current = new AtomicInteger(0);
        private final AtomicInteger successes = new AtomicInteger(0);
        private final AtomicInteger drops = new AtomicInteger(0);
        private volatile long lastAdjustment = System.currentTimeMillis();
        private static final long ADJUSTMENT_INTERVAL_MS = 1000;

        AdaptiveConcurrencyLimiter(int initialLimit, int minLimit, int maxLimit) {
            this.limit = initialLimit;
            this.minLimit = minLimit;
            this.maxLimit = maxLimit;
        }

        @Override
        public synchronized boolean tryAcquire() {
            adjustLimit();
            if (current.get() < limit) {
                current.incrementAndGet();
                return true;
            }
            return false;
        }

        @Override
        public synchronized void onSuccess() {
            current.decrementAndGet();
            successes.incrementAndGet();
        }

        @Override
        public synchronized void onDropped() {
            current.decrementAndGet();
            drops.incrementAndGet();
        }

        private void adjustLimit() {
            long now = System.currentTimeMillis();
            if (now - lastAdjustment < ADJUSTMENT_INTERVAL_MS) {
                return;
            }

            lastAdjustment = now;

            int total = successes.get() + drops.get();
            if (total > 10) {
                double successRate = (double) successes.get() / total;

                if (successRate > 0.9 && limit < maxLimit) {
                    limit++;

                } else if (successRate < 0.5 && limit > minLimit) {
                    limit--;
                }

                successes.set(0);
                drops.set(0);
            }
        }

        int getLimit() {
            return limit;
        }
    }

    @Test
    void testRetryWithAimdConcurrencyLimiter() {
        AimdConcurrencyLimiter limiter = new AimdConcurrencyLimiter(5, 10);
        AtomicInteger attemptCount = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
            .maxAttempts(3)
            .execute(RetryControls.concurrencyLimited(() -> {
                attemptCount.incrementAndGet();
                return "success";
            }, limiter))
            .getResult();

        assertEquals("success", result);
        assertEquals(1, attemptCount.get());
    }

    @Test
    void testRetryWithAimdConcurrencyLimiter_Limited() {
        AimdConcurrencyLimiter limiter = new AimdConcurrencyLimiter(1, 10);
        limiter.tryAcquire();

        RetryException exception = assertThrows(RetryException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(3)
                    .execute(RetryControls.concurrencyLimited(() -> "success", limiter))
                    .getResult());

        assertInstanceOf(ConcurrencyLimitedException.class, exception.getCause());
    }

    @Test
    void testRetryWithCustomFixedConcurrencyLimiter() {
        FixedConcurrencyLimiter limiter = new FixedConcurrencyLimiter(2);
        AtomicInteger attemptCount = new AtomicInteger(0);

        String result1 = Retry.<String>newBuilder()
            .maxAttempts(1)
            .execute(RetryControls.concurrencyLimited(() -> {
                attemptCount.incrementAndGet();
                return "success1";
            }, limiter))
            .getResult();

        assertEquals("success1", result1);
        assertEquals(1, attemptCount.get());

        String result2 = Retry.<String>newBuilder()
            .maxAttempts(1)
            .execute(RetryControls.concurrencyLimited(() -> {
                attemptCount.incrementAndGet();
                return "success2";
            }, limiter))
            .getResult();

        assertEquals("success2", result2);
        assertEquals(2, attemptCount.get());
    }

    @Test
    void testRetryWithCustomFixedConcurrencyLimiter_Limited() {
        FixedConcurrencyLimiter limiter = new FixedConcurrencyLimiter(1);
        limiter.tryAcquire();

        RetryException exception = assertThrows(RetryException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(1)
                    .execute(RetryControls.concurrencyLimited(() -> "success", limiter))
                    .getResult());

        assertInstanceOf(ConcurrencyLimitedException.class, exception.getCause());
    }

    @Test
    void testRetryWithCustomFixedConcurrencyLimiter_ReleaseOnSuccess() {
        FixedConcurrencyLimiter limiter = new FixedConcurrencyLimiter(1);
        AtomicInteger attemptCount = new AtomicInteger(0);

        String result1 = Retry.<String>newBuilder()
            .maxAttempts(1)
            .execute(RetryControls.concurrencyLimited(() -> {
                attemptCount.incrementAndGet();
                return "success1";
            }, limiter))
            .getResult();

        assertEquals("success1", result1);

        String result2 = Retry.<String>newBuilder()
            .maxAttempts(1)
            .execute(RetryControls.concurrencyLimited(() -> {
                attemptCount.incrementAndGet();
                return "success2";
            }, limiter))
            .getResult();

        assertEquals("success2", result2);
        assertEquals(2, attemptCount.get());
    }

    @Test
    void testRetryWithCustomAdaptiveConcurrencyLimiter() {
        AdaptiveConcurrencyLimiter limiter = new AdaptiveConcurrencyLimiter(2, 1, 5);
        AtomicInteger attemptCount = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
            .maxAttempts(1)
            .execute(RetryControls.concurrencyLimited(() -> {
                attemptCount.incrementAndGet();
                return "success";
            }, limiter))
            .getResult();

        assertEquals("success", result);
        assertEquals(1, attemptCount.get());
        assertEquals(2, limiter.getLimit());
    }
}


