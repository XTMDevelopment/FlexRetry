package id.xtramile.flexretry.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RetryRegistry {
    private final Map<String, RetryConfig<?>> configs = new ConcurrentHashMap<>();

    public <T> void register(String name, RetryConfig<T> config) {
        configs.put(name, config);
    }

    @SuppressWarnings("unchecked")
    public <T> RetryTemplate<T> template(String name) {
        RetryConfig<T> cfg = (RetryConfig<T>) configs.get(name);

        if (cfg == null) {
            throw new IllegalArgumentException("No retry config named " + name);
        }

        return new RetryTemplate<>(cfg);
    }
}
