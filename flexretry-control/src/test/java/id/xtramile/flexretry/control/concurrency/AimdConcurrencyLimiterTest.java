package id.xtramile.flexretry.control.concurrency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AimdConcurrencyLimiterTest {

    @Test
    void testConstructor_ValidParameters() {
        AimdConcurrencyLimiter limiter = new AimdConcurrencyLimiter(5, 10);
        assertNotNull(limiter);
    }

    @Test
    void testConstructor_InitialLessThanOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new AimdConcurrencyLimiter(0, 10));
    }

    @Test
    void testConstructor_MaxLessThanInitial() {
        assertThrows(IllegalArgumentException.class,
                () -> new AimdConcurrencyLimiter(10, 5));
    }

    @Test
    void testTryAcquire_WithinLimit() {
        AimdConcurrencyLimiter limiter = new AimdConcurrencyLimiter(5, 10);
        
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire());
        }
        
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void testOnSuccess_IncreasesLimit() {
        AimdConcurrencyLimiter limiter = new AimdConcurrencyLimiter(5, 10);

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire());
        }

        assertFalse(limiter.tryAcquire());

        limiter.onSuccess();

        assertTrue(limiter.tryAcquire());
    }

    @Test
    void testOnSuccess_DoesNotExceedMax() {
        AimdConcurrencyLimiter limiter = new AimdConcurrencyLimiter(5, 10);

        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire();
            limiter.onSuccess();
        }

        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire());
        }

        assertFalse(limiter.tryAcquire());
    }

    @Test
    void testOnDropped_DecreasesLimit() {
        AimdConcurrencyLimiter limiter = new AimdConcurrencyLimiter(10, 20);

        limiter.tryAcquire();
        limiter.onDropped();

        int acquired = 0;
        while (limiter.tryAcquire()) {
            acquired++;
        }

        assertEquals(5, acquired);
    }

    @Test
    void testOnDropped_MinimumLimit() {
        AimdConcurrencyLimiter limiter = new AimdConcurrencyLimiter(2, 10);
        
        limiter.tryAcquire();
        limiter.onDropped();
        
        limiter.tryAcquire();
        limiter.onDropped();

        assertTrue(limiter.tryAcquire());
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        AimdConcurrencyLimiter limiter = new AimdConcurrencyLimiter(5, 10);
        
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        boolean[] results = new boolean[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;

            threads[i] = new Thread(() -> {
                results[index] = limiter.tryAcquire();

                if (results[index]) {
                    try {
                        Thread.sleep(10);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    limiter.onSuccess();
                }
            });

            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }

        int successes = 0;
        for (boolean result : results) {
            if (result) successes++;
        }

        assertTrue(successes >= 5);
    }

    @Test
    void testMultipleSuccesses() {
        AimdConcurrencyLimiter limiter = new AimdConcurrencyLimiter(3, 10);

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());

        limiter.onSuccess();
        limiter.onSuccess();
        limiter.onSuccess();

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
    }

    @Test
    void testMultipleDrops() {
        AimdConcurrencyLimiter limiter = new AimdConcurrencyLimiter(8, 10);

        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire();
            limiter.onDropped();
        }

        int acquired = 0;
        while (limiter.tryAcquire()) {
            acquired++;
        }

        assertTrue(acquired >= 1);
    }
}

