package id.xtramile.flexretry;

import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Container for retry hooks; defaults are no-ops.
 */
public final class RetryListeners<T> {
    public Consumer<RetryContext<T>> onAttempt = ctx -> {
    };
    public BiConsumer<T, RetryContext<T>> onSuccess = (result, ctx) -> {
    };
    public BiConsumer<Throwable, RetryContext<T>> onFailure = (ex, ctx) -> {
    };
    public Consumer<RetryContext<T>> onFinally = ctx -> {
    };

    public BiFunction<Duration, RetryContext<T>, Duration> beforeSleep = (duration, ctx) -> duration;

    public BiConsumer<T, RetryContext<T>> afterAttemptSuccess = (result, ctx) -> {
    };
    public BiConsumer<Throwable, RetryContext<T>> afterAttemptFailure = (ex, ctx) -> {
    };

    public Consumer<RetryContext<T>> onRecover = ctx -> {
    };

    public RetryListeners() {
    }

    public RetryListeners<T> onAttempt(Consumer<RetryContext<T>> onAttempt) {
        this.onAttempt = onAttempt;
        return this;
    }

    public RetryListeners<T> onSuccess(BiConsumer<T, RetryContext<T>> onSuccess) {
        this.onSuccess = onSuccess;
        return this;
    }

    public RetryListeners<T> onFailure(BiConsumer<Throwable, RetryContext<T>> onFailure) {
        this.onFailure = onFailure;
        return this;
    }

    public RetryListeners<T> onFinally(Consumer<RetryContext<T>> onFinally) {
        this.onFinally = onFinally;
        return this;
    }
}
