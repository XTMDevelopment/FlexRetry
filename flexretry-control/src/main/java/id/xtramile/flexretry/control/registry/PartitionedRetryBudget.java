package id.xtramile.flexretry.control.registry;

import id.xtramile.flexretry.control.budget.RetryBudget;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class PartitionedRetryBudget<K> {
    private final Function<K, RetryBudget> factory;
    private final Map<K, RetryBudget> map = new ConcurrentHashMap<>();

    public PartitionedRetryBudget(Function<K, RetryBudget> factory) {
        this.factory = factory;
    }

    public RetryBudget forKey(K key) {
        return map.computeIfAbsent(key, factory);
    }
}
