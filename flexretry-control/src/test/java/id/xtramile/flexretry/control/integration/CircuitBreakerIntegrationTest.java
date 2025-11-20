package id.xtramile.flexretry.control.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.RetryException;
import id.xtramile.flexretry.control.RetryControls;
import id.xtramile.flexretry.control.breaker.CircuitBreaker;
import id.xtramile.flexretry.control.breaker.CircuitOpenException;
import id.xtramile.flexretry.control.breaker.FailureAccrualPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CircuitBreaker with RetryExecutors.
 * Demonstrates custom FailureAccrualPolicy implementations.
 */
class CircuitBreakerIntegrationTest {

    /**
     * Custom FailureAccrualPolicy that trips after N consecutive failures
     */
    static class ConsecutiveFailurePolicy implements FailureAccrualPolicy {
        private final int threshold;
        private int consecutiveFailures = 0;

        ConsecutiveFailurePolicy(int threshold) {
            this.threshold = threshold;
        }

        @Override
        public boolean recordSuccess() {
            consecutiveFailures = 0;
            return false;
        }

        @Override
        public boolean recordFailure() {
            consecutiveFailures++;
            return consecutiveFailures >= threshold;
        }

        @Override
        public boolean isTripped() {
            return consecutiveFailures >= threshold;
        }

        @Override
        public void reset() {
            consecutiveFailures = 0;
        }
    }

    /**
     * Custom FailureAccrualPolicy that trips after N failures in a window
     */
    static class WindowedFailurePolicy implements FailureAccrualPolicy {
        private final int threshold;
        private final AtomicInteger failures = new AtomicInteger(0);

        WindowedFailurePolicy(int threshold) {
            this.threshold = threshold;
        }

        @Override
        public boolean recordSuccess() {
            failures.set(0);
            return false;
        }

        @Override
        public boolean recordFailure() {
            int current = failures.incrementAndGet();
            return current >= threshold;
        }

        @Override
        public boolean isTripped() {
            return failures.get() >= threshold;
        }

        @Override
        public void reset() {
            failures.set(0);
        }
    }

    /**
     * Custom FailureAccrualPolicy with exponential backoff on reset
     */
    static class ExponentialBackoffPolicy implements FailureAccrualPolicy {
        private final int initialThreshold;
        private int currentThreshold;
        private int failures = 0;

        ExponentialBackoffPolicy(int initialThreshold) {
            this.initialThreshold = initialThreshold;
            this.currentThreshold = initialThreshold;
        }

        @Override
        public boolean recordSuccess() {
            failures = 0;
            return false;
        }

        @Override
        public boolean recordFailure() {
            failures++;
            return failures >= currentThreshold;
        }

        @Override
        public boolean isTripped() {
            return failures >= currentThreshold;
        }

        @Override
        public void reset() {
            failures = 0;
            currentThreshold = Math.min(currentThreshold * 2, initialThreshold * 8);
        }
    }

    private FailureAccrualPolicy createSimplePolicy(int threshold) {
        final int finalThreshold = threshold;
        return new FailureAccrualPolicy() {
            private int failures = 0;

            @Override
            public boolean recordSuccess() {
                return true;
            }

            @Override
            public boolean recordFailure() {
                failures++;
                return failures >= finalThreshold;
            }

            @Override
            public boolean isTripped() {
                return failures >= finalThreshold;
            }

            @Override
            public void reset() {
                failures = 0;
            }
        };
    }

    @Test
    void testRetryWithCircuitBreaker() {
        FailureAccrualPolicy policy = createSimplePolicy(3);
        CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofSeconds(1));
        AtomicInteger attemptCount = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
            .maxAttempts(5)
            .execute(RetryControls.circuitBreak(breaker, () -> {
                attemptCount.incrementAndGet();
                return "success";
            }))
            .getResult();

        assertEquals("success", result);
        assertEquals(1, attemptCount.get());
    }

    @Test
    void testRetryWithCircuitBreaker_Open() {
        FailureAccrualPolicy policy = createSimplePolicy(2);
        CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofSeconds(1));

        breaker.onFailure();
        breaker.onFailure();

        id.xtramile.flexretry.RetryException exception = assertThrows(RetryException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(5)
                    .execute(RetryControls.circuitBreak(breaker, () -> "success"))
                    .getResult());

        assertInstanceOf(CircuitOpenException.class, exception.getCause());
    }

    @Test
    void testRetryWithCustomConsecutiveFailurePolicy() {
        ConsecutiveFailurePolicy policy = new ConsecutiveFailurePolicy(2);
        CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofSeconds(1));
        AtomicInteger attemptCount = new AtomicInteger(0);

        String result1 = Retry.<String>newBuilder()
            .maxAttempts(3)
            .execute(RetryControls.circuitBreak(breaker, () -> {
                attemptCount.incrementAndGet();
                return "success1";
            }))
            .getResult();

        assertEquals("success1", result1);
        assertEquals(1, attemptCount.get());

        breaker.onFailure();
        breaker.onFailure();

        // Circuit should be open
        id.xtramile.flexretry.RetryException exception = assertThrows(RetryException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(3)
                    .execute(RetryControls.circuitBreak(breaker, () -> "success2"))
                    .getResult());

        assertInstanceOf(CircuitOpenException.class, exception.getCause());
    }

    @Test
    void testRetryWithCustomWindowedFailurePolicy() {
        WindowedFailurePolicy policy = new WindowedFailurePolicy(3);
        CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofSeconds(1));
        AtomicInteger attemptCount = new AtomicInteger(0);

        breaker.onFailure();
        breaker.onFailure();
        breaker.onFailure();

        RetryException exception = assertThrows(RetryException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(3)
                    .execute(RetryControls.circuitBreak(breaker, () -> {
                        attemptCount.incrementAndGet();
                        return "success";
                    }))
                    .getResult());

        assertInstanceOf(CircuitOpenException.class, exception.getCause());
        assertEquals(0, attemptCount.get());
    }

    @Test
    void testRetryWithCustomExponentialBackoffPolicy() throws InterruptedException {
        ExponentialBackoffPolicy policy = new ExponentialBackoffPolicy(2);
        CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofMillis(100));

        breaker.onFailure();
        breaker.onFailure();

        assertFalse(breaker.allow());

        Thread.sleep(150);
        assertTrue(breaker.allow());

        policy.reset();

        breaker.onFailure();
        assertTrue(breaker.allow());

        breaker.onFailure();
        assertTrue(breaker.allow());

        breaker.onFailure();
        breaker.onFailure();
        assertFalse(breaker.allow());
    }

    @Test
    void testRetryWithCircuitBreaker_StateTransitions() {
        FailureAccrualPolicy policy = createSimplePolicy(2);
        CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofSeconds(1));

        assertTrue(breaker.allow());

        breaker.onFailure();
        breaker.onFailure();
        assertFalse(breaker.allow());

        try {
            Thread.sleep(1100);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertTrue(breaker.allow());

        breaker.onSuccess();
        assertTrue(breaker.allow());
    }
}