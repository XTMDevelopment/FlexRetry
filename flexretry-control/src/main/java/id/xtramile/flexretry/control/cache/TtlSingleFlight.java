package id.xtramile.flexretry.control.cache;

import id.xtramile.flexretry.control.sf.SingleFlight;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

public final class TtlSingleFlight<T> {
    private final SingleFlight<T> sf = new SingleFlight<>();
    private final Map<String, Entry<T>> cache = new ConcurrentHashMap<>();

    public Optional<T> get(String key) {
        Entry<T> entry = cache.get(key);

        if (entry == null || entry.isExpired()) {
            return Optional.empty();
        }

        return Optional.ofNullable(entry.value);
    }

    public T compute(String key, Callable<T> supplier, Duration ttl) throws Exception {
        Entry<T> entry = cache.get(key);

        if (entry != null && !entry.isExpired()) {
            return entry.value;
        }

        T result = sf.execute(key, supplier);

        cache.put(key, new Entry<>(result, ttl));
        return result;
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
