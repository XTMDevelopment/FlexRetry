package id.xtramile.flexretry.observability.metrics;

public interface RetryMetrics {
    static RetryMetrics noop() {
        return new RetryMetrics() {
            public void attemptStarted(String name, int attempt) {
            }

            public void attemptSucceeded(String name, int attempt) {
            }

            public void attemptFailed(String name, int attempt, Throwable error) {
            }

            public void exhausted(String name, int attempts, Throwable lastError) {
            }
        };
    }

    void attemptStarted(String name, int attempt);

    void attemptSucceeded(String name, int attempt);

    void attemptFailed(String name, int attempt, Throwable error);

    void exhausted(String name, int attempts, Throwable lastError);
}
