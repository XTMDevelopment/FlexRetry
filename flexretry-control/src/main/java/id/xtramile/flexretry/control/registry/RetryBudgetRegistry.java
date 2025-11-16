package id.xtramile.flexretry.control.registry;

import id.xtramile.flexretry.control.budget.RetryBudget;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RetryBudgetRegistry {
    private final Map<String, RetryBudget> map = new ConcurrentHashMap<>();

    public RetryBudget getOrRegister(String name, RetryBudget budget) {
        return map.computeIfAbsent(name, k -> budget);
    }
}
