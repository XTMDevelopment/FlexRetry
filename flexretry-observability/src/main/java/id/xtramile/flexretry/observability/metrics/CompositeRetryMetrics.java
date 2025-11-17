package id.xtramile.flexretry.observability.metrics;

import id.xtramile.flexretry.RetryContext;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class CompositeRetryMetrics<T> implements RetryMetrics<T> {
    private final List<RetryMetrics<T>> delegates;

    public CompositeRetryMetrics(List<? extends  RetryMetrics<T>> delegates) {
        Objects.requireNonNull(delegates, "delegates");
        this.delegates = List.copyOf(delegates);
    }

    @SafeVarargs
    public static <T> CompositeRetryMetrics<T> of(RetryMetrics<T>... metrics) {
        List<RetryMetrics<T>> list = new ArrayList<>();

        if (metrics != null) {
            Collections.addAll(list, metrics);
        }

        return new CompositeRetryMetrics<>(list);
    }

    @Override
    public void onScheduled(RetryContext<T> context, int attempt, Duration nextDelay) {
        for (RetryMetrics<T> delegate : delegates) {
            delegate.onScheduled(context, attempt, nextDelay);
        }
    }

    @Override
    public void onAttempt(RetryContext<T> context, int attempt) {
        for (RetryMetrics<T> delegate : delegates) {
            delegate.onAttempt(context, attempt);
        }
    }

    @Override
    public void onSuccess(RetryContext<T> context, int attempt, Duration elapsed) {
        for (RetryMetrics<T> delegate : delegates) {
            delegate.onSuccess(context, attempt, elapsed);
        }
    }

    @Override
    public void onFailure(RetryContext<T> context, int attempt, Throwable error, Duration elapsed) {
        for (RetryMetrics<T> delegate : delegates) {
            delegate.onFailure(context, attempt, error, elapsed);
        }
    }

    @Override
    public void onGiveUp(RetryContext<T> context, int attempt, Throwable error, Duration elapsed) {
        for (RetryMetrics<T> delegate : delegates) {
            delegate.onGiveUp(context, attempt, error, elapsed);
        }
    }

    public List<RetryMetrics<T>> getDelegates() {
        return delegates;
    }
}
