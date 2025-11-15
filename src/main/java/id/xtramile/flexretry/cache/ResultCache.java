package id.xtramile.flexretry.cache;

import java.time.Duration;
import java.util.Optional;

public interface ResultCache<K, V> {
    Optional<V> get(K key);

    void put(K key, V value, Duration ttl);
}
