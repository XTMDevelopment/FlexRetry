package id.xtramile.flexretry.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal stub so you can extend later to/from maps (no YAML/props deps).
 */
public final class RetryConfigCodec {
    public static Map<String, Object> toMap(RetryConfig<?> cfg) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", cfg.name);
        map.put("id", cfg.id);
        map.put("tags", cfg.tags);

        return map;
    }

    public static <T> RetryConfig<T> fromMap(Map<String, Object> map) {
        throw new UnsupportedOperationException("Define your mapping from names to strategies/policies");
    }
}
