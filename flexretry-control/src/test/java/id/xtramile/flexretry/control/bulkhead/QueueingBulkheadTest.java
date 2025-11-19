package id.xtramile.flexretry.control.bulkhead;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueueingBulkheadTest {

    @Test
    void testConstructor_ValidParameter() {
        QueueingBulkhead bulkhead = new QueueingBulkhead(5);
        assertNotNull(bulkhead);
    }

    @Test
    void testConstructor_ZeroMaxConcurrent() {
        assertThrows(IllegalArgumentException.class,
                () -> new QueueingBulkhead(0));
    }

    @Test
    void testConstructor_NegativeMaxConcurrent() {
        assertThrows(IllegalArgumentException.class,
                () -> new QueueingBulkhead(-1));
    }

    @Test
    void testTryAcquire_Success() throws InterruptedException {
        QueueingBulkhead bulkhead = new QueueingBulkhead(3);
        
        assertTrue(bulkhead.tryAcquire(100));
        assertTrue(bulkhead.tryAcquire(100));
        assertTrue(bulkhead.tryAcquire(100));
    }

    @Test
    void testTryAcquire_Timeout() throws InterruptedException {
        QueueingBulkhead bulkhead = new QueueingBulkhead(1);
        
        assertTrue(bulkhead.tryAcquire(100));

        long start = System.currentTimeMillis();
        assertFalse(bulkhead.tryAcquire(50));
        long elapsed = System.currentTimeMillis() - start;
        
        assertTrue(elapsed >= 45);
        assertTrue(elapsed < 100);
    }

    @Test
    void testTryAcquire_AcquiresAfterRelease() throws InterruptedException {
        QueueingBulkhead bulkhead = new QueueingBulkhead(1);
        
        assertTrue(bulkhead.tryAcquire(100));

        Thread releaseThread = new Thread(() -> {
            try {
                Thread.sleep(50);
                bulkhead.release();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        releaseThread.start();

        assertTrue(bulkhead.tryAcquire(200));
        
        releaseThread.join();
    }

    @Test
    void testRelease() throws InterruptedException {
        QueueingBulkhead bulkhead = new QueueingBulkhead(2);
        
        assertTrue(bulkhead.tryAcquire(100));
        assertTrue(bulkhead.tryAcquire(100));
        assertFalse(bulkhead.tryAcquire(50));
        
        bulkhead.release();
        assertTrue(bulkhead.tryAcquire(100));
    }

    @Test
    void testInterruptedException() throws InterruptedException {
        QueueingBulkhead bulkhead = new QueueingBulkhead(1);
        
        assertTrue(bulkhead.tryAcquire(100));
        
        Thread currentThread = Thread.currentThread();
        Thread interruptThread = new Thread(() -> {
            try {
                Thread.sleep(10);
                currentThread.interrupt();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        interruptThread.start();
        
        assertThrows(InterruptedException.class,
                () -> bulkhead.tryAcquire(1000));
        
        interruptThread.join();
    }
}

