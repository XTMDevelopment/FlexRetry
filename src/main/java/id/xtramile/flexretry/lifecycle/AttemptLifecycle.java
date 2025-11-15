package id.xtramile.flexretry.lifecycle;

import id.xtramile.flexretry.RetryContext;

public interface AttemptLifecycle<T> {
    default void beforeAttempt(RetryContext<T> ctx) {}
    default void afterSuccess(RetryContext<T> ctx) {}
    default void afterFailure(RetryContext<T> ctx, Throwable error) {}
}
