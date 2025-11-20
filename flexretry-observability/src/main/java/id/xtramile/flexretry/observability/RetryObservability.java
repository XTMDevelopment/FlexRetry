package id.xtramile.flexretry.observability;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.RetryContext;
import id.xtramile.flexretry.RetryOutcome;
import id.xtramile.flexretry.lifecycle.AttemptLifecycle;
import id.xtramile.flexretry.observability.events.*;
import id.xtramile.flexretry.observability.metrics.CompositeRetryMetrics;
import id.xtramile.flexretry.observability.metrics.RetryMetrics;
import id.xtramile.flexretry.observability.metrics.SimpleRetryMetrics;
import id.xtramile.flexretry.observability.trace.SimpleTraceContext;
import id.xtramile.flexretry.observability.trace.TraceContext;
import id.xtramile.flexretry.observability.trace.TraceScope;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Static utility methods to integrate observability features (metrics, events, tracing)
 * with Retry.Builder, similar to RetryControls.
 */
public final class RetryObservability {
    private RetryObservability() {
    }

    /* -------------------- METRICS -------------------- */
    public static <T> Retry.Builder<T> metrics(Retry.Builder<T> builder, RetryMetrics<T> metrics) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(metrics, "metrics");

        if (metrics == RetryMetrics.noop()) {
            return builder;
        }

        TimingTracker<T> tracker = new TimingTracker<>();

        builder.onAttempt(ctx -> {
            tracker.onAttempt(ctx);
            metrics.onAttempt(ctx, ctx.attempt());
        });

        builder.afterAttemptFailure((error, ctx) -> {
            Duration elapsed = tracker.getElapsed(ctx);
            metrics.onFailure(ctx, ctx.attempt(), error, elapsed);
        });

        builder.beforeSleep((delay, ctx) -> {
            metrics.onScheduled(ctx, ctx.attempt(), delay);
            return delay;
        });

        builder.onSuccess((result, ctx) -> {
            Duration elapsed = tracker.getTotalElapsed(ctx);
            metrics.onSuccess(ctx, ctx.attempt(), elapsed);
        });

        builder.onFailure((error, ctx) -> {
            Duration elapsed = tracker.getTotalElapsed(ctx);

            if (ctx.attempt() >= ctx.maxAttempts()) {
                metrics.onGiveUp(ctx, ctx.attempt(), error, elapsed);

            } else {
                metrics.onFailure(ctx, ctx.attempt(), error, elapsed);
            }
        });

        return builder;
    }

    /* -------------------- EVENTS -------------------- */
    public static <T> Retry.Builder<T> events(Retry.Builder<T> builder, RetryEventBus<T> eventBus) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(eventBus, "eventBus");

        if (eventBus == RetryEventBus.noop()) {
            return builder;
        }

        builder.onAttempt(ctx -> {
            RetryEvent<T> event = RetryEvent.<T>builder(RetryEventType.RETRY_ATTEMPT)
                    .context(ctx)
                    .attempt(ctx.attempt())
                    .lastError(ctx.lastError())
                    .nextDelay(ctx.nextDelay())
                    .build();

            eventBus.publish(event);
        });

        builder.afterAttemptSuccess((result, ctx) -> {
            RetryEvent<T> event = RetryEvent.<T>builder(RetryEventType.RETRY_SUCCESS)
                    .context(ctx)
                    .attempt(ctx.attempt())
                    .outcome(new RetryOutcome<>(true, result, null, ctx.attempt()))
                    .build();

            eventBus.publish(event);
        });

        builder.afterAttemptFailure((error, ctx) -> {
            RetryEvent<T> event = RetryEvent.<T>builder(RetryEventType.RETRY_FAILURE)
                    .context(ctx)
                    .attempt(ctx.attempt())
                    .lastError(error)
                    .nextDelay(ctx.nextDelay())
                    .build();

            eventBus.publish(event);
        });

        builder.beforeSleep((delay, ctx) -> {
            RetryEvent<T> event = RetryEvent.<T>builder(RetryEventType.RETRY_SCHEDULED)
                    .context(ctx)
                    .attempt(ctx.attempt())
                    .lastError(ctx.lastError())
                    .nextDelay(delay)
                    .build();

            eventBus.publish(event);
            return delay;
        });

        builder.onSuccess((result, ctx) -> {
            RetryEvent<T> event = RetryEvent.<T>builder(RetryEventType.RETRY_SUCCESS)
                    .context(ctx)
                    .attempt(ctx.attempt())
                    .outcome(new RetryOutcome<>(true, result, null, ctx.attempt()))
                    .build();

            eventBus.publish(event);
        });

        builder.onFailure((error, ctx) -> {
            RetryEventType type = ctx.attempt() >= ctx.maxAttempts()
                    ? RetryEventType.RETRY_GIVE_UP
                    : RetryEventType.RETRY_FAILURE;

            RetryEvent<T> event = RetryEvent.<T>builder(type)
                    .context(ctx)
                    .attempt(ctx.attempt())
                    .lastError(error)
                    .outcome(new RetryOutcome<>(false, null, error, ctx.attempt()))
                    .build();

            eventBus.publish(event);
        });

        return builder;
    }

    /* -------------------- TRACING -------------------- */
    public static <T> Retry.Builder<T> tracing(Retry.Builder<T> builder, TraceContext traceContext) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(traceContext, "traceContext");

        if (traceContext == TraceContext.noop()) {
            return builder;
        }

        builder.lifecycle(new AttemptLifecycle<T>() {
            private final ThreadLocal<TraceScope> currentSpan = new ThreadLocal<>();

            @Override
            public void beforeAttempt(RetryContext<T> ctx) {
                Map<String, String> attributes = Map.of(
                        "retry.id", ctx.id(),
                        "retry.name", ctx.name(),
                        "retry.attempt", String.valueOf(ctx.attempt()),
                        "retry.maxAttempts", String.valueOf(ctx.maxAttempts())
                );
                TraceScope span = traceContext.withSpan("retry.attempt", attributes);

                currentSpan.set(span);
            }

            @Override
            public void afterSuccess(RetryContext<T> ctx) {
                TraceScope span = currentSpan.get();
                if (span != null) {
                    try {
                        span.close();

                    } catch (Exception ignored) {
                    } finally {
                        currentSpan.remove();
                    }
                }
            }

            @Override
            public void afterFailure(RetryContext<T> ctx, Throwable error) {
                TraceScope span = currentSpan.get();
                if (span != null) {
                    try {
                        span.close();

                    } catch (Exception ignored) {
                    } finally {
                        currentSpan.remove();
                    }
                }
            }
        });

        return builder;
    }

    /* -------------------- ALL OBSERVABILITY -------------------- */
    public static <T> Retry.Builder<T> observability(
            Retry.Builder<T> builder,
            RetryMetrics<T> metrics,
            RetryEventBus<T> eventBus,
            TraceContext traceContext
    ) {
        Objects.requireNonNull(builder, "builder");

        if (metrics != null && metrics != RetryMetrics.noop()) {
            metrics(builder, metrics);
        }

        if (eventBus != null && eventBus != RetryEventBus.noop()) {
            events(builder, eventBus);
        }

        if (traceContext != null && traceContext != TraceContext.noop()) {
            tracing(builder, traceContext);
        }

        return builder;
    }

    /* -------------------- CONVENIENCE FACTORY METHODS -------------------- */
    public static <T> SimpleRetryEventBus<T> simpleEventBus(RetryEventListener<T> listener) {
        Objects.requireNonNull(listener, "listener");

        SimpleRetryEventBus<T> bus = SimpleRetryEventBus.create();
        bus.register(listener);

        return bus;
    }

    @SafeVarargs
    public static <T> SimpleRetryEventBus<T> simpleEventBus(RetryEventListener<T>... listeners) {
        SimpleRetryEventBus<T> bus = SimpleRetryEventBus.create();

        if (listeners != null) {
            for (RetryEventListener<T> listener : listeners) {
                if (listener != null) {
                    bus.register(listener);
                }
            }
        }

        return bus;
    }

    @SafeVarargs
    public static <T> CompositeRetryMetrics<T> compositeMetrics(RetryMetrics<T>... metrics) {
        return CompositeRetryMetrics.of(metrics);
    }

    public static <T> SimpleRetryMetrics.Builder<T> simpleMetrics() {
        return SimpleRetryMetrics.builder();
    }

    public static SimpleTraceContext.Builder simpleTraceContext() {
        return SimpleTraceContext.builder();
    }

    /* -------------------- CUSTOMIZABLE OBSERVABILITY -------------------- */
    public static <T> Retry.Builder<T> events(
            Retry.Builder<T> builder,
            RetryEventBus<T> eventBus,
            Predicate<RetryEvent<T>> eventFilter,
            Function<RetryEvent<T>, RetryEvent<T>> eventTransformer
    ) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(eventBus, "eventBus");

        if (eventBus == RetryEventBus.noop()) {
            return builder;
        }

        Function<RetryEvent<T>, RetryEvent<T>> transformer = eventTransformer != null ? eventTransformer : e -> e;
        Predicate<RetryEvent<T>> filter = eventFilter != null ? eventFilter : event -> true;

        builder.onAttempt(ctx -> {
            RetryEvent<T> event = transformer.apply(RetryEvent.<T>builder(RetryEventType.RETRY_ATTEMPT)
                    .context(ctx)
                    .attempt(ctx.attempt())
                    .lastError(ctx.lastError())
                    .nextDelay(ctx.nextDelay())
                    .build());

            if (filter.test(event)) {
                eventBus.publish(event);
            }
        });

        builder.afterAttemptSuccess((result, ctx) -> {
            RetryEvent<T> event = transformer.apply(RetryEvent.<T>builder(RetryEventType.RETRY_SUCCESS)
                    .context(ctx)
                    .attempt(ctx.attempt())
                    .outcome(new RetryOutcome<>(true, result, null, ctx.attempt()))
                    .build());

            if (filter.test(event)) {
                eventBus.publish(event);
            }
        });

        builder.afterAttemptFailure((error, ctx) -> {
            RetryEvent<T> event = transformer.apply(RetryEvent.<T>builder(RetryEventType.RETRY_FAILURE)
                    .context(ctx)
                    .attempt(ctx.attempt())
                    .lastError(error)
                    .nextDelay(ctx.nextDelay())
                    .build());

            if (filter.test(event)) {
                eventBus.publish(event);
            }
        });

        builder.beforeSleep((delay, ctx) -> {
            RetryEvent<T> event = transformer.apply(RetryEvent.<T>builder(RetryEventType.RETRY_SCHEDULED)
                    .context(ctx)
                    .attempt(ctx.attempt())
                    .lastError(ctx.lastError())
                    .nextDelay(delay)
                    .build());

            if (filter.test(event)) {
                eventBus.publish(event);
            }
            return delay;
        });

        builder.onSuccess((result, ctx) -> {
            RetryEvent<T> event = transformer.apply(RetryEvent.<T>builder(RetryEventType.RETRY_SUCCESS)
                    .context(ctx)
                    .attempt(ctx.attempt())
                    .outcome(new RetryOutcome<>(true, result, null, ctx.attempt()))
                    .build());

            if (filter.test(event)) {
                eventBus.publish(event);
            }
        });

        builder.onFailure((error, ctx) -> {
            RetryEventType type = ctx.attempt() >= ctx.maxAttempts()
                    ? RetryEventType.RETRY_GIVE_UP
                    : RetryEventType.RETRY_FAILURE;

            RetryEvent<T> event = transformer.apply(RetryEvent.<T>builder(type)
                    .context(ctx)
                    .attempt(ctx.attempt())
                    .lastError(error)
                    .outcome(new RetryOutcome<>(false, null, error, ctx.attempt()))
                    .build());

            if (filter.test(event)) {
                eventBus.publish(event);
            }
        });

        return builder;
    }

    public static <T> Retry.Builder<T> tracing(
            Retry.Builder<T> builder,
            TraceContext traceContext,
            Function<RetryContext<T>, String> spanNameProvider,
            Function<RetryContext<T>, Map<String, String>> attributeProvider
    ) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(traceContext, "traceContext");

        if (traceContext == TraceContext.noop()) {
            return builder;
        }

        Function<RetryContext<T>, String> nameProvider = spanNameProvider != null
                ? spanNameProvider
                : ctx -> "retry.attempt";

        Function<RetryContext<T>, Map<String, String>> attrProvider = attributeProvider != null
                ? attributeProvider
                : ctx -> Map.of(
                "retry.id", ctx.id(),
                "retry.name", ctx.name(),
                "retry.attempt", String.valueOf(ctx.attempt()),
                "retry.maxAttempts", String.valueOf(ctx.maxAttempts())
        );

        builder.lifecycle(new AttemptLifecycle<T>() {
            private final ThreadLocal<TraceScope> currentSpan = new ThreadLocal<>();

            @Override
            public void beforeAttempt(RetryContext<T> ctx) {
                String spanName = nameProvider.apply(ctx);

                Map<String, String> attributes = attrProvider.apply(ctx);
                TraceScope span = traceContext.withSpan(spanName, attributes);

                currentSpan.set(span);
            }

            @Override
            public void afterSuccess(RetryContext<T> ctx) {
                TraceScope span = currentSpan.get();
                if (span != null) {
                    try {
                        span.close();

                    } catch (Exception ignored) {
                    } finally {
                        currentSpan.remove();
                    }
                }
            }

            @Override
            public void afterFailure(RetryContext<T> ctx, Throwable error) {
                TraceScope span = currentSpan.get();
                if (span != null) {
                    try {
                        span.close();

                    } catch (Exception ignored) {
                    } finally {
                        currentSpan.remove();
                    }
                }
            }
        });

        return builder;
    }
}