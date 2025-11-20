package id.xtramile.flexretry.control.breaker;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerTest {

    @Test
    void testConstructor() {
        FailureAccrualPolicy policy = createSimplePolicy(3);
        CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofSeconds(1));
        
        assertNotNull(breaker);
        assertTrue(breaker.allow());
    }

    @Test
    void testAllow_ClosedState() {
        FailureAccrualPolicy policy = createSimplePolicy(3);
        CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofSeconds(1));

        assertTrue(breaker.allow());
        assertTrue(breaker.allow());
    }

    @Test
    void testAllow_OpenState() {
        FailureAccrualPolicy policy = createSimplePolicy(2);
        CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofSeconds(1));

        breaker.onFailure();
        breaker.onFailure();

        assertFalse(breaker.allow());
    }

    @Test
    void testAllow_HalfOpenAfterTimeout() throws InterruptedException {
        FailureAccrualPolicy policy = createSimplePolicy(2);
        CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofMillis(100));

        breaker.onFailure();
        breaker.onFailure();
        assertFalse(breaker.allow());

        Thread.sleep(150);

        assertTrue(breaker.allow());
    }

    @Test
    void testOnSuccess_ClosesCircuit() {
        FailureAccrualPolicy policy = createSimplePolicy(2);
        CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofSeconds(1));

        breaker.onFailure();
        breaker.onFailure();
        assertFalse(breaker.allow());

        try {
            Thread.sleep(150);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        breaker.allow();
        breaker.onSuccess();

        assertTrue(breaker.allow());
    }

    @Test
    void testOnFailure_TripsCircuit() {
        FailureAccrualPolicy policy = createSimplePolicy(2);
        CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofSeconds(1));
        
        assertTrue(breaker.allow());
        
        breaker.onFailure();
        assertTrue(breaker.allow());
        
        breaker.onFailure();
        assertFalse(breaker.allow());
    }

    @Test
    void testOnSuccess_ResetsPolicy() {
        FailureAccrualPolicy policy = createSimplePolicy(2);
        CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofSeconds(1));

        breaker.onFailure();
        breaker.onFailure();

        try {
            Thread.sleep(150);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        breaker.allow();
        breaker.onSuccess();

        breaker.onFailure();
        assertTrue(breaker.allow());
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        FailureAccrualPolicy policy = createSimplePolicy(10);
        CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofSeconds(1));
        
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        boolean[] results = new boolean[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;

            threads[i] = new Thread(() -> {
                results[index] = breaker.allow();

                if (results[index]) {
                    if (index % 2 == 0) {
                        breaker.onSuccess();

                    } else {
                        breaker.onFailure();
                    }
                }
            });

            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }

        for (boolean result : results) {
            assertTrue(result);
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
}

