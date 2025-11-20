package id.xtramile.flexretry.control.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketRateLimiterTest {

    @Test
    void testConstructor_ValidParameters() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 5);
        assertNotNull(limiter);
    }

    @Test
    void testConstructor_CapacityLessThanOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenBucketRateLimiter(0, 5));
    }

    @Test
    void testConstructor_RefillPerSecondLessThanOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenBucketRateLimiter(10, 0));
    }

    @Test
    void testTryAcquire_InitialCapacity() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 10);

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire());
        }

        assertFalse(limiter.tryAcquire());
    }

    @Test
    void testTryAcquire_Refill() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 10);

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire());
        }

        assertFalse(limiter.tryAcquire());

        Thread.sleep(150);

        assertTrue(limiter.tryAcquire());
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 100);
        
        int threadCount = 20;
        Thread[] threads = new Thread[threadCount];
        boolean[] results = new boolean[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;

            threads[i] = new Thread(() -> results[index] = limiter.tryAcquire());

            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }

        int successes = 0;
        for (boolean result : results) {
            if (result) successes++;
        }

        assertTrue(successes > 0);
        assertTrue(successes <= 10);
    }

    @Test
    void testRefill_Rate() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 10);

        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire());
        }

        Thread.sleep(1100);

        int acquired = 0;
        for (int i = 0; i < 10; i++) {
            if (limiter.tryAcquire()) {
                acquired++;
            }
        }
        
        assertTrue(acquired >= 9);
    }
}

