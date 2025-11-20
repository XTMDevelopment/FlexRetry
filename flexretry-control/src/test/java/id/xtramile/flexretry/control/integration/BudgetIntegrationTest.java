package id.xtramile.flexretry.control.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.control.RetryControls;
import id.xtramile.flexretry.control.budget.RetryBudget;
import id.xtramile.flexretry.control.budget.TokenBucketRetryBudget;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RetryBudget with RetryExecutors.
 * Demonstrates custom RetryBudget implementations.
 */
class BudgetIntegrationTest {

    /**
     * Custom RetryBudget that allows a fixed number of retries
     * Note: maxRetries represents the maximum total attempts allowed
     * (including the initial attempt)
     */
    static class FixedCountBudget implements RetryBudget {
        private final int maxAttempts;
        private final AtomicInteger attempts = new AtomicInteger(0);

        FixedCountBudget(int maxRetries) {
            this.maxAttempts = maxRetries;
        }

        @Override
        public boolean tryAcquire() {
            int currentAttempts = attempts.get() + 1;

            if (currentAttempts < maxAttempts) {
                attempts.incrementAndGet();
                return true;
            }

            return false;
        }
    }

    /**
     * Custom RetryBudget that tracks usage and allows reset
     */
    static class ResettableBudget implements RetryBudget {
        private final int maxRetries;
        private volatile int used = 0;

        ResettableBudget(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        @Override
        public synchronized boolean tryAcquire() {
            if (used < maxRetries) {
                used++;
                return true;
            }

            return false;
        }

        synchronized void reset() {
            used = 0;
        }

        synchronized int getUsed() {
            return used;
        }
    }

    /**
     * Custom RetryBudget that allows retries based on percentage
     */
    static class PercentageBudget implements RetryBudget {
        private final double percentage;
        private final AtomicInteger total = new AtomicInteger(0);
        private final AtomicInteger allowed = new AtomicInteger(0);

        PercentageBudget(double percentage) {
            this.percentage = percentage;
        }

        @Override
        public boolean tryAcquire() {
            int currentTotal = total.incrementAndGet();
            int shouldAllow = (int) Math.round(currentTotal * percentage);

            if (allowed.get() < shouldAllow) {
                allowed.incrementAndGet();
                return true;
            }

            return false;
        }
    }

    @Test
    void testRetryWithTokenBucketBudget() {
        TokenBucketRetryBudget budget = new TokenBucketRetryBudget(10.0, 50.0);
        AtomicInteger attemptCount = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
            .maxAttempts(5)
            .policy(RetryControls.withBudget(
                (r, e, a, m) -> e != null,
                budget
            ))
            .execute((Callable<String>) () -> {
                attemptCount.incrementAndGet();

                if (attemptCount.get() < 3) {
                    throw new RuntimeException("retry");
                }

                return "success";
            })
            .getResult();

        assertEquals("success", result);
        assertTrue(attemptCount.get() >= 3);
    }

    @Test
    void testRetryWithTokenBucketBudget_Exhausted() {
        TokenBucketRetryBudget budget = new TokenBucketRetryBudget(2.0, 2.0);
        budget.tryAcquire();
        budget.tryAcquire();

        AtomicInteger attemptCount = new AtomicInteger(0);

        assertThrows(RuntimeException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(5)
                    .policy(RetryControls.withBudget(
                        (r, e, a, m) -> e != null,
                        budget
                    ))
                    .execute((Callable<String>) () -> {
                        attemptCount.incrementAndGet();
                        throw new RuntimeException("retry");
                    })
                    .getResult());

        assertTrue(attemptCount.get() > 0);
    }

    @Test
    void testRetryWithCustomFixedCountBudget() {
        FixedCountBudget budget = new FixedCountBudget(3);
        AtomicInteger attemptCount = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
            .maxAttempts(10)
            .policy(RetryControls.withBudget(
                (r, e, a, m) -> e != null,
                budget
            ))
            .execute((Callable<String>) () -> {
                attemptCount.incrementAndGet();

                if (attemptCount.get() < 3) {
                    throw new RuntimeException("retry");
                }

                return "success";
            })
            .getResult();

        assertEquals("success", result);
        assertEquals(3, attemptCount.get());
    }

    @Test
    void testRetryWithCustomFixedCountBudget_Exhausted() {
        FixedCountBudget budget = new FixedCountBudget(2);
        AtomicInteger attemptCount = new AtomicInteger(0);

        assertThrows(RuntimeException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(10)
                    .policy(RetryControls.withBudget(
                        (r, e, a, m) -> e != null,
                        budget
                    ))
                    .execute((Callable<String>) () -> {
                        attemptCount.incrementAndGet();
                        throw new RuntimeException("retry");
                    })
                    .getResult());

        assertEquals(2, attemptCount.get());
    }

    @Test
    void testRetryWithCustomResettableBudget() {
        ResettableBudget budget = new ResettableBudget(2);
        AtomicInteger attemptCount = new AtomicInteger(0);

        String result1 = Retry.<String>newBuilder()
            .maxAttempts(5)
            .policy(RetryControls.withBudget(
                (r, e, a, m) -> e != null,
                budget
            ))
            .execute((Callable<String>) () -> {
                attemptCount.incrementAndGet();

                if (attemptCount.get() < 2) {
                    throw new RuntimeException("retry");
                }

                return "success1";
            })
            .getResult();

        assertEquals("success1", result1);
        assertEquals(2, attemptCount.get());
        assertEquals(1, budget.getUsed());

        budget.reset();
        attemptCount.set(0);

        String result2 = Retry.<String>newBuilder()
            .maxAttempts(5)
            .policy(RetryControls.withBudget(
                (r, e, a, m) -> e != null,
                budget
            ))
            .execute((Callable<String>) () -> {
                attemptCount.incrementAndGet();

                if (attemptCount.get() < 2) {
                    throw new RuntimeException("retry");
                }

                return "success2";
            })
            .getResult();

        assertEquals("success2", result2);
        assertEquals(2, attemptCount.get());
        assertEquals(1, budget.getUsed());
    }

    @Test
    void testRetryWithCustomPercentageBudget() {
        PercentageBudget budget = new PercentageBudget(0.5);
        AtomicInteger attemptCount = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
            .maxAttempts(10)
            .policy(RetryControls.withBudget(
                (r, e, a, m) -> e != null,
                budget
            ))
            .execute((Callable<String>) () -> {
                attemptCount.incrementAndGet();

                if (attemptCount.get() < 2) {
                    throw new RuntimeException("retry");
                }

                return "success";
            })
            .getResult();

        assertEquals("success", result);
        assertTrue(attemptCount.get() >= 2);
    }

    @Test
    void testRetryWithUnlimitedBudget() {
        RetryBudget budget = RetryBudget.unlimited();
        AtomicInteger attemptCount = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
            .maxAttempts(5)
            .policy(RetryControls.withBudget(
                (r, e, a, m) -> e != null,
                budget
            ))
            .execute((Callable<String>) () -> {
                attemptCount.incrementAndGet();

                if (attemptCount.get() < 3) {
                    throw new RuntimeException("retry");
                }

                return "success";
            })
            .getResult();

        assertEquals("success", result);
        assertEquals(3, attemptCount.get());
    }
}

