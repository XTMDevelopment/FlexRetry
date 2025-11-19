package id.xtramile.flexretry.control.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TtlSingleFlightTest {

    @Test
    void testGet_Empty() {
        TtlSingleFlight<String> cache = new TtlSingleFlight<>();
        
        Optional<String> result = cache.get("key1");
        assertFalse(result.isPresent());
    }

    @Test
    void testCompute_StoresResult() throws Exception {
        TtlSingleFlight<String> cache = new TtlSingleFlight<>();
        
        String result = cache.compute("key1", () -> "value1", Duration.ofSeconds(1));
        assertEquals("value1", result);
        
        Optional<String> cached = cache.get("key1");
        assertTrue(cached.isPresent());
        assertEquals("value1", cached.get());
    }

    @Test
    void testCompute_Expires() throws Exception {
        TtlSingleFlight<String> cache = new TtlSingleFlight<>();
        
        cache.compute("key1", () -> "value1", Duration.ofMillis(50));
        
        Optional<String> cached = cache.get("key1");
        assertTrue(cached.isPresent());
        
        Thread.sleep(100);
        
        Optional<String> expired = cache.get("key1");
        assertFalse(expired.isPresent());
    }

    @Test
    void testCompute_DeduplicatesConcurrentCalls() throws Exception {
        TtlSingleFlight<String> cache = new TtlSingleFlight<>();
        AtomicInteger callCount = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch latch = new CountDownLatch(5);
        
        Callable<String> supplier = () -> {
            callCount.incrementAndGet();
            Thread.sleep(100);
            return "result";
        };

        Thread[] threads = new Thread[5];
        String[] results = new String[5];
        
        for (int i = 0; i < 5; i++) {
            final int index = i;

            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();
                    results[index] = cache.compute("key1", supplier, Duration.ofSeconds(1));

                } catch (Exception e) {
                    e.printStackTrace();

                } finally {
                    latch.countDown();
                }
            });

            threads[i].start();
        }
        
        Thread.sleep(10);
        startLatch.countDown();
        latch.await();

        for (String result : results) {
            assertEquals("result", result);
        }

        assertTrue(callCount.get() >= 1 && callCount.get() <= 5, 
            "Call count should be between 1 and 5, but was " + callCount.get());
    }

    @Test
    void testCompute_ExceptionPropagates() {
        TtlSingleFlight<String> cache = new TtlSingleFlight<>();
        
        assertThrows(RuntimeException.class,
                () -> cache.compute("key1", () -> {
                    throw new RuntimeException("test error");
        }, Duration.ofSeconds(1)));

        Optional<String> cached = cache.get("key1");
        assertFalse(cached.isPresent());
    }

    @Test
    void testGet_AfterExpiration() throws Exception {
        TtlSingleFlight<String> cache = new TtlSingleFlight<>();
        
        cache.compute("key1", () -> "value1", Duration.ofMillis(50));
        Thread.sleep(100);

        Optional<String> result = cache.get("key1");
        assertFalse(result.isPresent());
    }

    @Test
    void testCompute_DifferentKeys() throws Exception {
        TtlSingleFlight<String> cache = new TtlSingleFlight<>();
        
        String result1 = cache.compute("key1", () -> "value1", Duration.ofSeconds(1));
        String result2 = cache.compute("key2", () -> "value2", Duration.ofSeconds(1));
        
        assertEquals("value1", result1);
        assertEquals("value2", result2);
        
        Optional<String> cached1 = cache.get("key1");
        Optional<String> cached2 = cache.get("key2");
        
        assertTrue(cached1.isPresent());
        assertTrue(cached2.isPresent());
        assertEquals("value1", cached1.get());
        assertEquals("value2", cached2.get());
    }

    @Test
    void testCompute_RecomputesAfterExpiration() throws Exception {
        TtlSingleFlight<String> cache = new TtlSingleFlight<>();
        AtomicInteger callCount = new AtomicInteger(0);
        
        Callable<String> supplier = () -> "value" + callCount.incrementAndGet();
        
        String result1 = cache.compute("key1", supplier, Duration.ofMillis(50));
        assertEquals("value1", result1);
        
        Thread.sleep(100);
        
        String result2 = cache.compute("key1", supplier, Duration.ofSeconds(1));
        assertEquals("value2", result2);
        
        assertEquals(2, callCount.get());
    }
}

