package id.xtramile.flexretry.policy;

/**
 * Decides if we should retry given the outcome of an attempt
 */
public interface RetryPolicy<T> {
    boolean shouldRetry(T result, Throwable error, int attempt, int maxAttempts);
}
