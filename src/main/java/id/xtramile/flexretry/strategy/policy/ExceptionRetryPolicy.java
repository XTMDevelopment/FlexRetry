package id.xtramile.flexretry.strategy.policy;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Retries when the thrown exception matches one of the configured types
 */
public final class ExceptionRetryPolicy<T> implements RetryPolicy<T> {
    private final Set<Class<? extends Throwable>> retryOn;

    @SafeVarargs
    public ExceptionRetryPolicy(Class<? extends Throwable>... retryOn) {
        this.retryOn = new HashSet<>(Arrays.asList(retryOn));
    }

    @Override
    public boolean shouldRetry(T result, Throwable error, int attempt, int maxAttempts) {
        if (error == null) {
            return false;
        }

        if (attempt >= maxAttempts) {
            return false;
        }

        if (retryOn.isEmpty()) {
            return false;
        }

        for (Class<? extends Throwable> retryOnClass : retryOn) {
            if (retryOnClass.isInstance(error)) {
                return true;
            }
        }

        return false;
    }
}
