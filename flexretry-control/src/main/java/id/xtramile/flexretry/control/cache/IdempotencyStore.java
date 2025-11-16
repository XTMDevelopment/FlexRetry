package id.xtramile.flexretry.control.cache;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class IdempotencyStore<V> {
    private final Map<String, Entry<V>> map = new ConcurrentHashMap<>();

    public Optional<V> get(String key) {
        Entry<V> entry = map.get(key);

        if (entry == null || entry.isExpired()) {
            return Optional.empty();
        }

        return Optional.ofNullable(entry.value);
    }

    public void put(String key, V value, Duration ttl) {
        map.put(key, new Entry<>(value, ttl));
    }

    private static final class Entry<V> {
        final V value;
        final long expiresAtNanos;

        Entry(V value, Duration ttl) {
            this.value = value;
            this.expiresAtNanos = System.nanoTime() + ttl.toNanos();
        }

        boolean isExpired() {
            return System.nanoTime() > expiresAtNanos;
        }
    }
}
