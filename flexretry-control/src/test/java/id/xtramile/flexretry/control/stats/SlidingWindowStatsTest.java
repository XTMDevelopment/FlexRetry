package id.xtramile.flexretry.control.stats;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowStatsTest {

    @Test
    void testConstructor() {
        SlidingWindowStats stats = new SlidingWindowStats(Duration.ofSeconds(1));
        assertNotNull(stats);
    }

    @Test
    void testRecordSuccess() {
        SlidingWindowStats stats = new SlidingWindowStats(Duration.ofSeconds(1));
        
        stats.recordSuccess();
        assertEquals(1, stats.successes());
        
        stats.recordSuccess();
        assertEquals(2, stats.successes());
    }

    @Test
    void testRecordFailure() {
        SlidingWindowStats stats = new SlidingWindowStats(Duration.ofSeconds(1));
        
        stats.recordFailure();
        assertEquals(1, stats.failures());
        
        stats.recordFailure();
        assertEquals(2, stats.failures());
    }

    @Test
    void testSuccesses_Expires() throws InterruptedException {
        SlidingWindowStats stats = new SlidingWindowStats(Duration.ofMillis(100));
        
        stats.recordSuccess();
        stats.recordSuccess();
        assertEquals(2, stats.successes());
        
        Thread.sleep(150);
        
        assertEquals(0, stats.successes());
    }

    @Test
    void testFailures_Expires() throws InterruptedException {
        SlidingWindowStats stats = new SlidingWindowStats(Duration.ofMillis(100));
        
        stats.recordFailure();
        stats.recordFailure();
        assertEquals(2, stats.failures());
        
        Thread.sleep(150);
        
        assertEquals(0, stats.failures());
    }

    @Test
    void testMixed() {
        SlidingWindowStats stats = new SlidingWindowStats(Duration.ofSeconds(1));
        
        stats.recordSuccess();
        stats.recordFailure();
        stats.recordSuccess();
        stats.recordFailure();
        
        assertEquals(2, stats.successes());
        assertEquals(2, stats.failures());
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        SlidingWindowStats stats = new SlidingWindowStats(Duration.ofSeconds(1));
        
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;

            threads[i] = new Thread(() -> {
                if (index % 2 == 0) {
                    stats.recordSuccess();

                } else {
                    stats.recordFailure();
                }
            });

            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        assertEquals(5, stats.successes());
        assertEquals(5, stats.failures());
    }

    @Test
    void testPurge() throws InterruptedException {
        SlidingWindowStats stats = new SlidingWindowStats(Duration.ofMillis(50));

        for (int i = 0; i < 10; i++) {
            stats.recordSuccess();
            Thread.sleep(10);
        }

        assertTrue(stats.successes() < 10);
    }
}

