package id.xtramile.flexretry;

import id.xtramile.flexretry.policy.CompositeRetryPolicy;
import id.xtramile.flexretry.policy.ExceptionRetryPolicy;
import id.xtramile.flexretry.policy.ResultPredicateRetryPolicy;
import id.xtramile.flexretry.policy.RetryPolicy;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Public facade with a fluent Builder
 */
public final class Retry<T> {
    private Retry() {}

    public static <T> Builder<T> newBuilder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        private int maxAttempts = 3;
        private BackoffStrategy backoff = BackoffStrategy.fixed(Duration.ZERO);
        private Predicate<T> resultPredicate = result -> false;
        private Class<? extends Throwable>[] retryOn = new Class[0];

        private final RetryListeners<T> listeners = new RetryListeners<>();
        private Callable<T> task;

        private Sleeper sleeper = Sleeper.system();

        public Builder<T> maxAttempts(int attempts) {
            if (attempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be >= 1");
            }

            this.maxAttempts = attempts;
            return this;
        }

        public Builder<T> delayMillis(long millis) {
            this.backoff = BackoffStrategy.fixed(Duration.ofMillis(Math.max(0L, millis)));
            return this;
        }

        public Builder<T> backoff(BackoffStrategy backoff) {
            this.backoff = Objects.requireNonNull(backoff, "backoff");
            return this;
        }

        @SafeVarargs
        public final Builder<T> retryOn(Class<? extends Throwable>... errors) {
            this.retryOn = errors == null ? new Class[0] : errors.clone();
            return this;
        }

        public Builder<T> retryIf(Predicate<T> predicate) {
            this.resultPredicate = Objects.requireNonNull(predicate, "predicate");
            return this;
        }

        public Builder<T> onAttempt(Consumer<RetryContext<T>> consumer) {
            listeners.onAttempt(consumer);
            return this;
        }

        public Builder<T> onSuccess(BiConsumer<T, RetryContext<T>> consumer) {
            listeners.onSuccess(consumer);
            return this;
        }

        public Builder<T> onFailure(BiConsumer<Throwable, RetryContext<T>> consumer) {
            listeners.onFailure(consumer);
            return this;
        }

        public Builder<T> onFinally(Consumer<RetryContext<T>> consumer) {
            listeners.onFinally(consumer);
            return this;
        }

        public Builder<T> onSuccess(Consumer<T> consumer) {
            return onSuccess((result, ctx) -> consumer.accept(result));
        }

        public Builder<T> onFailure(Consumer<Throwable> consumer) {
            return onFailure((exception, ctx) -> consumer.accept(exception));
        }

        public Builder<T> execute(Supplier<T> supplier) {
            Objects.requireNonNull(supplier, "supplier");
            this.task = supplier::get;
            return this;
        }

        public Builder<T> execute(Callable<T> callable) {
            this.task = Objects.requireNonNull(callable, "callable");
            return this;
        }

        Builder<T> sleeper(Sleeper sleeper) {
            this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
            return this;
        }

        public RetryExecutor<T> buildExecutor() {
            if (task == null) {
                throw new IllegalStateException("No task provided. Call execute(...) first.");
            }

            RetryPolicy<T> policy = new CompositeRetryPolicy<>(
                    new ExceptionRetryPolicy<>(retryOn),
                    new ResultPredicateRetryPolicy<>(resultPredicate)
            );

            return new RetryExecutor<>(maxAttempts, backoff, policy, listeners, sleeper);
        }

        public T getResult() {
            return buildExecutor().run(task);
        }
    }
}
