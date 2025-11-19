package id.xtramile.flexretry.control.bulkhead;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BulkheadTest {

    @Test
    void testConstructor_ValidParameter() {
        Bulkhead bulkhead = new Bulkhead(5);
        assertNotNull(bulkhead);
    }

    @Test
    void testConstructor_ZeroMaxConcurrent() {
        assertThrows(IllegalArgumentException.class,
                () -> new Bulkhead(0));
    }

    @Test
    void testConstructor_NegativeMaxConcurrent() {
        assertThrows(IllegalArgumentException.class,
                () -> new Bulkhead(-1));
    }

    @Test
    void testTryAcquire_Success() {
        Bulkhead bulkhead = new Bulkhead(3);
        
        assertTrue(bulkhead.tryAcquire());
        assertTrue(bulkhead.tryAcquire());
        assertTrue(bulkhead.tryAcquire());
    }

    @Test
    void testTryAcquire_Full() {
        Bulkhead bulkhead = new Bulkhead(2);
        
        assertTrue(bulkhead.tryAcquire());
        assertTrue(bulkhead.tryAcquire());
        assertFalse(bulkhead.tryAcquire());
    }

    @Test
    void testRelease() {
        Bulkhead bulkhead = new Bulkhead(2);
        
        assertTrue(bulkhead.tryAcquire());
        assertTrue(bulkhead.tryAcquire());
        assertFalse(bulkhead.tryAcquire());
        
        bulkhead.release();
        assertTrue(bulkhead.tryAcquire());
    }

    @Test
    void testMultipleAcquireRelease() {
        Bulkhead bulkhead = new Bulkhead(3);
        
        for (int i = 0; i < 10; i++) {
            assertTrue(bulkhead.tryAcquire());
            bulkhead.release();
        }
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        Bulkhead bulkhead = new Bulkhead(5);
        
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        boolean[] results = new boolean[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                results[index] = bulkhead.tryAcquire();

                if (results[index]) {
                    try {
                        Thread.sleep(10);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    bulkhead.release();
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
}

