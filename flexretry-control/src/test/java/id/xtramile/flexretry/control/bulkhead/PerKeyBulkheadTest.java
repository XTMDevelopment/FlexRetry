package id.xtramile.flexretry.control.bulkhead;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerKeyBulkheadTest {

    @Test
    void testConstructor_ValidParameter() {
        PerKeyBulkhead<String> bulkhead = new PerKeyBulkhead<>(5);
        assertNotNull(bulkhead);
    }

    @Test
    void testConstructor_ZeroPermitsPerKey() {
        assertThrows(IllegalArgumentException.class,
                () -> new PerKeyBulkhead<>(0));
    }

    @Test
    void testConstructor_NegativePermitsPerKey() {
        assertThrows(IllegalArgumentException.class,
                () -> new PerKeyBulkhead<>(-1));
    }

    @Test
    void testTryAcquire_DifferentKeys() {
        PerKeyBulkhead<String> bulkhead = new PerKeyBulkhead<>(2);

        assertTrue(bulkhead.tryAcquire("key1"));
        assertTrue(bulkhead.tryAcquire("key1"));
        assertFalse(bulkhead.tryAcquire("key1"));
        
        assertTrue(bulkhead.tryAcquire("key2"));
        assertTrue(bulkhead.tryAcquire("key2"));
        assertFalse(bulkhead.tryAcquire("key2"));
    }

    @Test
    void testRelease() {
        PerKeyBulkhead<String> bulkhead = new PerKeyBulkhead<>(2);
        
        assertTrue(bulkhead.tryAcquire("key1"));
        assertTrue(bulkhead.tryAcquire("key1"));
        assertFalse(bulkhead.tryAcquire("key1"));
        
        bulkhead.release("key1");
        assertTrue(bulkhead.tryAcquire("key1"));
    }

    @Test
    void testRelease_NonExistentKey() {
        PerKeyBulkhead<String> bulkhead = new PerKeyBulkhead<>(2);

        assertDoesNotThrow(() -> bulkhead.release("nonexistent"));
    }

    @Test
    void testMultipleKeys() {
        PerKeyBulkhead<String> bulkhead = new PerKeyBulkhead<>(3);

        assertTrue(bulkhead.tryAcquire("key1"));
        assertTrue(bulkhead.tryAcquire("key2"));
        assertTrue(bulkhead.tryAcquire("key3"));
        
        assertTrue(bulkhead.tryAcquire("key1"));
        assertTrue(bulkhead.tryAcquire("key2"));
        assertTrue(bulkhead.tryAcquire("key3"));
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        PerKeyBulkhead<String> bulkhead = new PerKeyBulkhead<>(5);
        
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        boolean[] results = new boolean[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                results[index] = bulkhead.tryAcquire("sharedKey");

                if (results[index]) {
                    try {
                        Thread.sleep(10);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    bulkhead.release("sharedKey");
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
    void testIntegerKeys() {
        PerKeyBulkhead<Integer> bulkhead = new PerKeyBulkhead<>(2);
        
        assertTrue(bulkhead.tryAcquire(1));
        assertTrue(bulkhead.tryAcquire(1));
        assertFalse(bulkhead.tryAcquire(1));
        
        assertTrue(bulkhead.tryAcquire(2));
        assertTrue(bulkhead.tryAcquire(2));
        assertFalse(bulkhead.tryAcquire(2));
    }
}

