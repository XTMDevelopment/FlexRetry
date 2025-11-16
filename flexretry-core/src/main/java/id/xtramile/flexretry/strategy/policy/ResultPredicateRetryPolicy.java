package id.xtramile.flexretry.strategy.policy;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Retries based on result predicate (e.g., null/empty/HTTP status not OK)
 */
public final class ResultPredicateRetryPolicy<T> implements RetryPolicy<T> {
    private final Predicate<T> predicate;

    public ResultPredicateRetryPolicy(final Predicate<T> predicate) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
    }

    @Override
    public boolean shouldRetry(T result, Throwable error, int attempt, int maxAttempts) {
        if (error != null) {
            return false;
        }

        if (attempt >= maxAttempts) {
            return false;
        }

        return predicate.test(result);
    }
}
