package id.xtramile.flexretry.control.bulkhead;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public final class PerKeyBulkhead<K> {
    private final int permitsPerKey;
    private final Map<K, Semaphore> map = new ConcurrentHashMap<>();

    public PerKeyBulkhead(int permitsPerKey) {
        if (permitsPerKey < 1) {
            throw new IllegalArgumentException("permitsPerKey >= 1");
        }

        this.permitsPerKey = permitsPerKey;
    }

    public boolean tryAcquire(K key) {
        return map.computeIfAbsent(key, k -> new Semaphore(permitsPerKey, true)).tryAcquire();
    }

    public void release(K key) {
        Semaphore semaphore = map.get(key);

        if (semaphore != null) {
            semaphore.release();
        }
    }
}
