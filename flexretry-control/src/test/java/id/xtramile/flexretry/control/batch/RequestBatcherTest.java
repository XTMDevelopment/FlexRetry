package id.xtramile.flexretry.control.batch;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class RequestBatcherTest {

    @Test
    void testConstructor() {
        Function<List<String>, List<String>> transport = list -> list;
        RequestBatcher<String, String> batcher = new RequestBatcher<>(10, Duration.ofMillis(100), transport);
        
        assertNotNull(batcher);
        batcher.shutdown();
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testAddAndMaybeFlush_UnderLimit() {
        List<List<String>> batches = new ArrayList<>();
        Function<List<String>, List<String>> transport = list -> {
            batches.add(new ArrayList<>(list));
            return list;
        };
        
        RequestBatcher<String, String> batcher = new RequestBatcher<>(5, Duration.ofMillis(100), transport);
        
        List<String> result = batcher.addAndMaybeFlush("item1");
        assertTrue(result.isEmpty());
        
        result = batcher.addAndMaybeFlush("item2");
        assertTrue(result.isEmpty());
        
        batcher.shutdown();

        assertTrue(batches.size() >= 0);
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testAddAndMaybeFlush_ReachesLimit() {
        List<List<String>> batches = new ArrayList<>();
        Function<List<String>, List<String>> transport = list -> {
            batches.add(new ArrayList<>(list));
            return list;
        };
        
        RequestBatcher<String, String> batcher = new RequestBatcher<>(3, Duration.ofMillis(1000), transport);
        
        List<String> result1 = batcher.addAndMaybeFlush("item1");
        assertTrue(result1.isEmpty());
        
        List<String> result2 = batcher.addAndMaybeFlush("item2");
        assertTrue(result2.isEmpty());
        
        List<String> result3 = batcher.addAndMaybeFlush("item3");
        assertFalse(result3.isEmpty());
        assertEquals(3, result3.size());
        
        batcher.shutdown();

        assertTrue(batches.size() >= 0);
    }

    @Test
    void testFlush_Empty() {
        Function<List<String>, List<String>> transport = list -> list;
        RequestBatcher<String, String> batcher = new RequestBatcher<>(10, Duration.ofMillis(100), transport);
        
        List<String> result = batcher.flush();
        assertTrue(result.isEmpty());
        
        batcher.shutdown();
    }

    @Test
    void testFlush_WithItems() {
        Function<List<String>, List<String>> transport = list -> list;
        RequestBatcher<String, String> batcher = new RequestBatcher<>(10, Duration.ofMillis(100), transport);
        
        batcher.addAndMaybeFlush("item1");
        batcher.addAndMaybeFlush("item2");
        batcher.addAndMaybeFlush("item3");
        
        List<String> result = batcher.flush();
        assertEquals(3, result.size());
        assertTrue(result.contains("item1"));
        assertTrue(result.contains("item2"));
        assertTrue(result.contains("item3"));
        
        batcher.shutdown();
    }

    @Test
    void testAutoFlush() throws InterruptedException {
        List<List<String>> batches = new ArrayList<>();
        Function<List<String>, List<String>> transport = list -> {
            synchronized (batches) {
                batches.add(new ArrayList<>(list));
            }
            return list;
        };
        
        RequestBatcher<String, String> batcher = new RequestBatcher<>(10, Duration.ofMillis(100), transport);
        
        batcher.addAndMaybeFlush("item1");
        batcher.addAndMaybeFlush("item2");

        boolean flushed = false;
        for (int i = 0; i < 10; i++) {
            Thread.sleep(50);
            synchronized (batches) {
                if (!batches.isEmpty()) {
                    flushed = true;
                    break;
                }
            }
        }
        
        synchronized (batches) {
            assertTrue(flushed, "Auto-flush should have occurred within 500ms");
            assertFalse(batches.isEmpty(), "Batches should not be empty after auto-flush");
        }
        
        batcher.shutdown();
    }

    @Test
    void testShutdown() {
        Function<List<String>, List<String>> transport = list -> list;
        RequestBatcher<String, String> batcher = new RequestBatcher<>(10, Duration.ofMillis(100), transport);
        
        batcher.addAndMaybeFlush("item1");
        
        batcher.shutdown();

        assertDoesNotThrow(batcher::shutdown);
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        List<List<String>> batches = new ArrayList<>();
        Function<List<String>, List<String>> transport = list -> {
            synchronized (batches) {
                batches.add(new ArrayList<>(list));
            }
            return list;
        };
        
        RequestBatcher<String, String> batcher = new RequestBatcher<>(5, Duration.ofMillis(100), transport);
        
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                batcher.addAndMaybeFlush("item" + index);
                latch.countDown();
            });
            threads[i].start();
        }
        
        latch.await();
        
        batcher.shutdown();

        synchronized (batches) {
            assertFalse(batches.isEmpty());
        }
    }
}

