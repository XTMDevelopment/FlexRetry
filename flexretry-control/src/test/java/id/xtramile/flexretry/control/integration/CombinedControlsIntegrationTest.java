package id.xtramile.flexretry.control.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.control.RetryControls;
import id.xtramile.flexretry.control.budget.TokenBucketRetryBudget;
import id.xtramile.flexretry.control.bulkhead.Bulkhead;
import id.xtramile.flexretry.control.ratelimit.TokenBucketRateLimiter;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for combining multiple controls with RetryExecutors.
 */
class CombinedControlsIntegrationTest {

    @Test
    void testRetryWithCombinedControls() {
        Bulkhead bulkhead = new Bulkhead(5);
        TokenBucketRetryBudget budget = new TokenBucketRetryBudget(10.0, 50.0);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 10);

        AtomicInteger attemptCount = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
            .maxAttempts(5)
            .policy(RetryControls.withBudget(
                (r, e, a, m) -> e != null,
                budget
            ))
            .execute(RetryControls.rateLimited(
                RetryControls.bulkhead(bulkhead, (Supplier<String>) () -> {
                    attemptCount.incrementAndGet();

                    if (attemptCount.get() < 2) {
                        throw new RuntimeException("retry");
                    }

                    return "success";
                }),
                limiter
            ))
            .getResult();

        assertEquals("success", result);
        assertTrue(attemptCount.get() >= 2);
    }

    @Test
    void testRetryWithBudgetAndBulkhead() {
        Bulkhead bulkhead = new Bulkhead(3);
        TokenBucketRetryBudget budget = new TokenBucketRetryBudget(5.0, 10.0);
        AtomicInteger attemptCount = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
            .maxAttempts(5)
            .policy(RetryControls.withBudget(
                (r, e, a, m) -> e != null,
                budget
            ))
            .execute(RetryControls.bulkhead(bulkhead, (Supplier<String>) () -> {
                attemptCount.incrementAndGet();

                if (attemptCount.get() < 2) {
                    throw new RuntimeException("retry");
                }

                return "success";
            }))
            .getResult();

        assertEquals("success", result);
        assertTrue(attemptCount.get() >= 2);
    }

    @Test
    void testRetryWithBudgetAndRateLimiter() {
        TokenBucketRetryBudget budget = new TokenBucketRetryBudget(5.0, 10.0);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 5);
        AtomicInteger attemptCount = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
            .maxAttempts(5)
            .policy(RetryControls.withBudget(
                (r, e, a, m) -> e != null,
                budget
            ))
            .execute(RetryControls.rateLimited(() -> {
                attemptCount.incrementAndGet();

                if (attemptCount.get() < 2) {
                    throw new RuntimeException("retry");
                }

                return "success";
            }, limiter))
            .getResult();

        assertEquals("success", result);
        assertTrue(attemptCount.get() >= 2);
    }

    @Test
    void testRetryWithBulkheadAndRateLimiter() {
        Bulkhead bulkhead = new Bulkhead(3);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 5);
        AtomicInteger attemptCount = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
            .maxAttempts(3)
            .execute(RetryControls.rateLimited(
                RetryControls.bulkhead(bulkhead, (Supplier<String>) () -> {
                    attemptCount.incrementAndGet();
                    return "success";
                }),
                limiter
            ))
            .getResult();

        assertEquals("success", result);
        assertEquals(1, attemptCount.get());
    }

    @Test
    void testRetryWithAllControls() {
        Bulkhead bulkhead = new Bulkhead(5);
        TokenBucketRetryBudget budget = new TokenBucketRetryBudget(10.0, 50.0);
        TokenBucketRateLimiter rateLimiter = new TokenBucketRateLimiter(10, 10);
        AtomicInteger attemptCount = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
            .maxAttempts(5)
            .policy(RetryControls.withBudget(
                (r, e, a, m) -> e != null,
                budget
            ))
            .execute(RetryControls.rateLimited(
                RetryControls.bulkhead(bulkhead, (Supplier<String>) () -> {
                    attemptCount.incrementAndGet();

                    if (attemptCount.get() < 2) {
                        throw new RuntimeException("retry");
                    }

                    return "success";
                }),
                rateLimiter
            ))
            .getResult();

        assertEquals("success", result);
        assertTrue(attemptCount.get() >= 2);
    }
}


