package id.xtramile.flexretry.strategy.policy;

public final class MaxSameErrorPolicy<T> implements RetryPolicy<T> {
    private final int limit;
    private Throwable lastError;
    private int count;

    public MaxSameErrorPolicy(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit >= 1");
        }

        this.limit = limit;
    }

    @Override
    public boolean shouldRetry(T result, Throwable error, int attempt, int maxAttempts) {
        if (error == null || attempt >= maxAttempts) {
            return false;
        }

        if (lastError != null && lastError.getClass().equals(error.getClass())) {
            count++;

        } else {
            count = 1;
            lastError = error;
        }

        return count < limit;
    }
}
