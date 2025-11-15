package id.xtramile.flexretry.policy;

import java.util.Arrays;
import java.util.List;

/**
 * OR-composition of multiple policies. If any says "retry", we retry.
 */
public final class CompositeRetryPolicy<T> implements RetryPolicy<T> {
    private final List<RetryPolicy<T>> policies;

    @SafeVarargs
    public CompositeRetryPolicy(RetryPolicy<T>... policies) {
        this.policies = Arrays.asList(policies);
    }

    @Override
    public boolean shouldRetry(T result, Throwable error, int attempt, int maxAttempts) {
        for (RetryPolicy<T> policy : policies) {
            if (policy != null && policy.shouldRetry(result, error, attempt, maxAttempts)) {
                return true;
            }
        }

        return false;
    }
}
