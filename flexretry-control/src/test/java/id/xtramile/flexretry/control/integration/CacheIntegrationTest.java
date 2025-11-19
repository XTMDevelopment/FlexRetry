package id.xtramile.flexretry.control.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.control.RetryControls;
import id.xtramile.flexretry.control.cache.ResultCache;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for ResultCache with RetryExecutors.
 * Demonstrates custom ResultCache implementations.
 */
class CacheIntegrationTest {

    /**
     * Custom ResultCache with TTL support
     */
    static class TtlResultCache<K, V> implements ResultCache<K, V> {
        private final Map<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();

        @Override
        public Optional<V> get(K key) {
            CacheEntry<V> entry = cache.get(key);

            if (entry == null || entry.isExpired()) {
                if (entry != null) {
                    cache.remove(key);
                }

                return Optional.empty();
            }

            return Optional.of(entry.value);
        }

        @Override
        public void put(K key, V value, Duration ttl) {
            cache.put(key, new CacheEntry<>(value, ttl));
        }

        private static class CacheEntry<V> {
            final V value;
            final long expiresAt;

            CacheEntry(V value, Duration ttl) {
                this.value = value;
                this.expiresAt = System.currentTimeMillis() + ttl.toMillis();
            }

            boolean isExpired() {
                return System.currentTimeMillis() > expiresAt;
            }
        }
    }

    /**
     * Custom ResultCache with size limit and LRU eviction
     */
    static class LruResultCache<K, V> implements ResultCache<K, V> {
        private final int maxSize;
        private final Map<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();
        private final Map<K, Long> accessOrder = new ConcurrentHashMap<>();
        private long accessCounter = 0;

        LruResultCache(int maxSize) {
            this.maxSize = maxSize;
        }

        @Override
        public Optional<V> get(K key) {
            CacheEntry<V> entry = cache.get(key);

            if (entry == null || entry.isExpired()) {
                if (entry != null) {
                    cache.remove(key);
                    accessOrder.remove(key);
                }

                return Optional.empty();
            }

            accessOrder.put(key, ++accessCounter);
            return Optional.of(entry.value);
        }

        @Override
        public void put(K key, V value, Duration ttl) {
            if (cache.size() >= maxSize && !cache.containsKey(key)) {
                evictLru();
            }

            cache.put(key, new CacheEntry<>(value, ttl));
            accessOrder.put(key, ++accessCounter);
        }

        private void evictLru() {
            K lruKey = accessOrder.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

            if (lruKey != null) {
                cache.remove(lruKey);
                accessOrder.remove(lruKey);
            }
        }

        private static class CacheEntry<V> {
            final V value;
            final long expiresAt;

            CacheEntry(V value, Duration ttl) {
                this.value = value;
                this.expiresAt = System.currentTimeMillis() + ttl.toMillis();
            }

            boolean isExpired() {
                return System.currentTimeMillis() > expiresAt;
            }
        }
    }

    /**
     * Custom ResultCache with statistics tracking
     */
    static class StatisticsResultCache<K, V> implements ResultCache<K, V> {
        private final Map<K, V> cache = new ConcurrentHashMap<>();
        private final AtomicInteger hits = new AtomicInteger(0);
        private final AtomicInteger misses = new AtomicInteger(0);
        private final AtomicInteger puts = new AtomicInteger(0);

        @Override
        public Optional<V> get(K key) {
            V value = cache.get(key);

            if (value != null) {
                hits.incrementAndGet();
                return Optional.of(value);
            }

            misses.incrementAndGet();
            return Optional.empty();
        }

        @Override
        public void put(K key, V value, Duration ttl) {
            cache.put(key, value);
            puts.incrementAndGet();
        }

        int getHits() {
            return hits.get();
        }

        int getMisses() {
            return misses.get();
        }

        int getPuts() {
            return puts.get();
        }

        double getHitRate() {
            int total = hits.get() + misses.get();
            return total > 0 ? (double) hits.get() / total : 0.0;
        }
    }

    @Test
    void testRetryWithCaching() {
        ResultCache<String, String> cache = new ResultCache<>() {
            private final Map<String, String> map = new ConcurrentHashMap<>();

            @Override
            public Optional<String> get(String key) {
                return Optional.ofNullable(map.get(key));
            }

            @Override
            public void put(String key, String value, Duration ttl) {
                map.put(key, value);
            }
        };

        AtomicInteger callCount = new AtomicInteger(0);

        String result1 = RetryControls.cachingSupplier(
            cache,
            Retry.<String>newBuilder()
                    .name("test")
                    .id("test-id")
                    .execute((Callable<String>) () -> "dummy")
                    .toConfig(),
            ctx -> ctx.id() + ":" + ctx.name(),
            Duration.ofSeconds(1),
            () -> {
                callCount.incrementAndGet();
                return "success";
            }
        ).get();

        assertEquals("success", result1);
        assertEquals(1, callCount.get());

        String result2 = RetryControls.cachingSupplier(
            cache,
            Retry.<String>newBuilder()
                    .name("test")
                    .id("test-id")
                    .execute((Callable<String>) () -> "dummy")
                    .toConfig(),
            ctx -> ctx.id() + ":" + ctx.name(),
            Duration.ofSeconds(1),
            () -> {
                callCount.incrementAndGet();
                return "success";
            }
        ).get();

        assertEquals("success", result2);
        assertEquals(1, callCount.get());
    }

    @Test
    void testRetryWithCustomTtlResultCache() {
        TtlResultCache<String, String> cache = new TtlResultCache<>();
        AtomicInteger callCount = new AtomicInteger(0);

        String result1 = RetryControls.cachingSupplier(
            cache,
            Retry.<String>newBuilder()
                    .name("test")
                    .id("test-id")
                    .execute((Callable<String>) () -> "dummy")
                    .toConfig(),
            ctx -> ctx.id() + ":" + ctx.name(),
            Duration.ofMillis(100),
            () -> {
                callCount.incrementAndGet();
                return "success";
            }
        ).get();

        assertEquals("success", result1);
        assertEquals(1, callCount.get());

        String result2 = RetryControls.cachingSupplier(
            cache,
            Retry.<String>newBuilder()
                    .name("test")
                    .id("test-id")
                    .execute((Callable<String>) () -> "dummy")
                    .toConfig(),
            ctx -> ctx.id() + ":" + ctx.name(),
            Duration.ofMillis(100),
            () -> {
                callCount.incrementAndGet();
                return "success";
            }
        ).get();

        assertEquals("success", result2);
        assertEquals(1, callCount.get());

        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String result3 = RetryControls.cachingSupplier(
            cache,
            Retry.<String>newBuilder()
                    .name("test")
                    .id("test-id")
                    .execute((Callable<String>) () -> "dummy")
                    .toConfig(),
            ctx -> ctx.id() + ":" + ctx.name(),
            Duration.ofMillis(100),
            () -> {
                callCount.incrementAndGet();
                return "success";
            }
        ).get();

        assertEquals("success", result3);
        assertEquals(2, callCount.get());
    }

    @Test
    void testRetryWithCustomLruResultCache() {
        LruResultCache<String, String> cache = new LruResultCache<>(2);
        AtomicInteger callCount = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            final int index = i;
            RetryControls.cachingSupplier(
                cache,
                Retry.<String>newBuilder()
                        .name("test" + index)
                        .id("test-id" + index)
                        .execute((Callable<String>) () -> "dummy")
                        .toConfig(),
                ctx -> ctx.id() + ":" + ctx.name(),
                Duration.ofSeconds(1),
                () -> {
                    callCount.incrementAndGet();
                    return "success" + index;
                }
            ).get();
        }

        assertEquals(2, callCount.get());

        RetryControls.cachingSupplier(
            cache,
            Retry.<String>newBuilder()
                    .name("test2")
                    .id("test-id2")
                    .execute((Callable<String>) () -> "dummy")
                    .toConfig(),
            ctx -> ctx.id() + ":" + ctx.name(),
            Duration.ofSeconds(1),
            () -> {
                callCount.incrementAndGet();
                return "success2";
            }
        ).get();

        assertEquals(3, callCount.get());
    }

    @Test
    void testRetryWithCustomStatisticsResultCache() {
        StatisticsResultCache<String, String> cache = new StatisticsResultCache<>();
        AtomicInteger callCount = new AtomicInteger(0);

        String result1 = RetryControls.cachingSupplier(
            cache,
            Retry.<String>newBuilder()
                    .name("test")
                    .id("test-id")
                    .execute((Callable<String>) () -> "dummy")
                    .toConfig(),
            ctx -> ctx.id() + ":" + ctx.name(),
            Duration.ofSeconds(1),
            () -> {
                callCount.incrementAndGet();
                return "success";
            }
        ).get();

        assertEquals("success", result1);
        assertEquals(1, callCount.get());
        assertEquals(0, cache.getHits());
        assertEquals(1, cache.getMisses());
        assertEquals(1, cache.getPuts());

        String result2 = RetryControls.cachingSupplier(
            cache,
            Retry.<String>newBuilder()
                    .name("test")
                    .id("test-id")
                    .execute((Callable<String>) () -> "dummy")
                    .toConfig(),
            ctx -> ctx.id() + ":" + ctx.name(),
            Duration.ofSeconds(1),
            () -> {
                callCount.incrementAndGet();
                return "success";
            }
        ).get();

        assertEquals("success", result2);
        assertEquals(1, callCount.get());
        assertEquals(1, cache.getHits());
        assertEquals(1, cache.getMisses());
        assertEquals(1, cache.getPuts());
        assertEquals(0.5, cache.getHitRate(), 0.01);
    }
}

