package id.xtramile.flexretry.observability.metrics;

import id.xtramile.flexretry.RetryContext;

import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Simple customizable metrics implementation that delegates to consumer functions.
 */
public final class SimpleRetryMetrics<T> implements RetryMetrics<T> {
    private final Consumer<RetryContext<T>> onScheduledHandler;
    private final BiConsumer<RetryContext<T>, Integer> onAttemptHandler;
    private final BiConsumer<RetryContext<T>, Duration> onSuccessHandler;
    private final TriConsumer<RetryContext<T>, Throwable, Duration> onFailureHandler;
    private final TriConsumer<RetryContext<T>, Throwable, Duration> onGiveUpHandler;

    private SimpleRetryMetrics(Builder<T> builder) {
        this.onScheduledHandler = builder.onScheduledHandler;
        this.onAttemptHandler = builder.onAttemptHandler;
        this.onSuccessHandler = builder.onSuccessHandler;
        this.onFailureHandler = builder.onFailureHandler;
        this.onGiveUpHandler = builder.onGiveUpHandler;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    @Override
    public void onScheduled(RetryContext<T> context, int attempt, Duration nextDelay) {
        if (onScheduledHandler != null) {
            onScheduledHandler.accept(context);
        }
    }

    @Override
    public void onAttempt(RetryContext<T> context, int attempt) {
        if (onAttemptHandler != null) {
            onAttemptHandler.accept(context, attempt);
        }
    }

    @Override
    public void onSuccess(RetryContext<T> context, int attempt, Duration elapsed) {
        if (onSuccessHandler != null) {
            onSuccessHandler.accept(context, elapsed);
        }
    }

    @Override
    public void onFailure(RetryContext<T> context, int attempt, Throwable error, Duration elapsed) {
        if (onFailureHandler != null) {
            onFailureHandler.accept(context, error, elapsed);
        }
    }

    @Override
    public void onGiveUp(RetryContext<T> context, int attempt, Throwable error, Duration elapsed) {
        if (onGiveUpHandler != null) {
            onGiveUpHandler.accept(context, error, elapsed);
        }
    }

    public static final class Builder<T> {
        private Consumer<RetryContext<T>> onScheduledHandler;
        private BiConsumer<RetryContext<T>, Integer> onAttemptHandler;
        private BiConsumer<RetryContext<T>, Duration> onSuccessHandler;
        private TriConsumer<RetryContext<T>, Throwable, Duration> onFailureHandler;
        private TriConsumer<RetryContext<T>, Throwable, Duration> onGiveUpHandler;

        private Builder() {
        }

        public Builder<T> onScheduled(Consumer<RetryContext<T>> handler) {
            this.onScheduledHandler = handler;
            return this;
        }

        public Builder<T> onAttempt(BiConsumer<RetryContext<T>, Integer> handler) {
            this.onAttemptHandler = handler;
            return this;
        }

        public Builder<T> onSuccess(BiConsumer<RetryContext<T>, Duration> handler) {
            this.onSuccessHandler = handler;
            return this;
        }

        public Builder<T> onFailure(TriConsumer<RetryContext<T>, Throwable, Duration> handler) {
            this.onFailureHandler = handler;
            return this;
        }

        public Builder<T> onGiveUp(TriConsumer<RetryContext<T>, Throwable, Duration> handler) {
            this.onGiveUpHandler = handler;
            return this;
        }

        public SimpleRetryMetrics<T> build() {
            return new SimpleRetryMetrics<>(this);
        }
    }

    @FunctionalInterface
    public interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }
}

