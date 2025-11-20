package id.xtramile.flexretry.control.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FixedWindowRateLimiterTest {

    @Test
    void testConstructor_ValidParameters() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(10, 1000);
        assertNotNull(limiter);
    }

    @Test
    void testConstructor_LimitLessThanOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new FixedWindowRateLimiter(0, 1000));
    }

    @Test
    void testConstructor_WindowMillisLessThanOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new FixedWindowRateLimiter(10, 0));
    }

    @Test
    void testTryAcquire_WithinLimit() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(5, 1000);
        
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire());
        }
    }

    @Test
    void testTryAcquire_ExceedsLimit() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, 1000);
        
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void testTryAcquire_WindowReset() throws InterruptedException {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, 100);

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());

        Thread.sleep(150);

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(10, 1000);
        
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

        assertEquals(10, successes);
    }

    @Test
    void testMultipleWindows() throws InterruptedException {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(2, 100);

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
        
        Thread.sleep(150);

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }
}

