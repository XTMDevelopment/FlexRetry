package id.xtramile.flexretry.policy;

public final class Policies {
    private Policies() {}

    @SafeVarargs
    public static <T> RetryPolicy<T> or(RetryPolicy<T>... policies) {
        return ((result, error, attempt, maxAttempts) -> {
            for (RetryPolicy<T> policy : policies) {
                if (policy != null && policy.shouldRetry(result, error, attempt, maxAttempts)) {
                    return true;
                }
            }

            return false;
        });
    }

    @SafeVarargs
    public static <T> RetryPolicy<T> and(RetryPolicy<T>... policies) {
        return (((result, error, attempt, maxAttempts) -> {
            for (RetryPolicy<T> policy : policies) {
                if (policy != null && policy.shouldRetry(result, error, attempt, maxAttempts)) {
                    return false;
                }
            }

            return true;
        }));
    }

    public static <T> RetryPolicy<T> not(RetryPolicy<T> policy) {
        return ((result, error, attempt, maxAttempts) -> policy == null ||
                !policy.shouldRetry(result, error, attempt, maxAttempts));
    }
}
