package id.xtramile.flexretry.control.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyStoreTest {

    @Test
    void testGet_Empty() {
        IdempotencyStore<String> store = new IdempotencyStore<>();
        
        Optional<String> result = store.get("key1");
        assertFalse(result.isPresent());
    }

    @Test
    void testPutAndGet() {
        IdempotencyStore<String> store = new IdempotencyStore<>();
        
        store.put("key1", "value1", Duration.ofSeconds(1));
        
        Optional<String> result = store.get("key1");
        assertTrue(result.isPresent());
        assertEquals("value1", result.get());
    }

    @Test
    void testPutAndGet_Expires() throws InterruptedException {
        IdempotencyStore<String> store = new IdempotencyStore<>();
        
        store.put("key1", "value1", Duration.ofMillis(50));
        
        Optional<String> result = store.get("key1");
        assertTrue(result.isPresent());
        
        Thread.sleep(100);
        
        Optional<String> expired = store.get("key1");
        assertFalse(expired.isPresent());
    }

    @Test
    void testPut_Overwrites() {
        IdempotencyStore<String> store = new IdempotencyStore<>();
        
        store.put("key1", "value1", Duration.ofSeconds(1));
        store.put("key1", "value2", Duration.ofSeconds(1));
        
        Optional<String> result = store.get("key1");
        assertTrue(result.isPresent());
        assertEquals("value2", result.get());
    }

    @Test
    void testGet_DifferentKeys() {
        IdempotencyStore<String> store = new IdempotencyStore<>();
        
        store.put("key1", "value1", Duration.ofSeconds(1));
        store.put("key2", "value2", Duration.ofSeconds(1));
        
        Optional<String> result1 = store.get("key1");
        Optional<String> result2 = store.get("key2");
        
        assertTrue(result1.isPresent());
        assertTrue(result2.isPresent());
        assertEquals("value1", result1.get());
        assertEquals("value2", result2.get());
    }

    @Test
    void testGet_NullValue() {
        IdempotencyStore<String> store = new IdempotencyStore<>();
        
        store.put("key1", null, Duration.ofSeconds(1));
        
        Optional<String> result = store.get("key1");
        assertFalse(result.isPresent());
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        IdempotencyStore<String> store = new IdempotencyStore<>();
        
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;

            threads[i] = new Thread(() -> {
                store.put("key" + index, "value" + index, Duration.ofSeconds(1));

                Optional<String> result = store.get("key" + index);
                assertTrue(result.isPresent());
                assertEquals("value" + index, result.get());
            });

            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
    }
}

