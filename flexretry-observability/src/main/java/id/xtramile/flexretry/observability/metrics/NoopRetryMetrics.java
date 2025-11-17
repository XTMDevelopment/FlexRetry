package id.xtramile.flexretry.observability.metrics;

public final class NoopRetryMetrics<T> implements RetryMetrics<T> {
    public static final NoopRetryMetrics<?> INSTANCE = new NoopRetryMetrics<>();

    private NoopRetryMetrics() {
    }
}
