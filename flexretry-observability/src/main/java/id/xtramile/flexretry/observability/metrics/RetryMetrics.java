package id.xtramile.flexretry.observability.metrics;

import id.xtramile.flexretry.RetryContext;

import java.time.Duration;

public interface RetryMetrics<T> {
    default void onScheduled(RetryContext<T> context, int attempt, Duration nextDelay) {
    }

    default void onAttempt(RetryContext<T> context, int attempt) {
    }

    default void onSuccess(RetryContext<T> context, int attempt, Duration elapsed) {
    }

    default void onFailure(RetryContext<T> context, int attempt, Throwable error, Duration elapsed) {
    }

    default void onGiveUp(RetryContext<T> context, int attempt, Throwable error, Duration elapsed) {
    }

    @SuppressWarnings("unchecked")
    static <T> RetryMetrics<T> noop() {
        return (RetryMetrics<T>) NoopRetryMetrics.INSTANCE;
    }
}
