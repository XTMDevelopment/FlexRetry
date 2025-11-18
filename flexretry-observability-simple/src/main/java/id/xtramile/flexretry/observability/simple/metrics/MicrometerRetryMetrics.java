package id.xtramile.flexretry.observability.simple.metrics;

import id.xtramile.flexretry.RetryContext;
import id.xtramile.flexretry.observability.metrics.RetryMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Objects;

public final class MicrometerRetryMetrics<T> implements RetryMetrics<T> {

    private final MeterRegistry registry;
    private final String metricPrefix;

    public MicrometerRetryMetrics(MeterRegistry registry, String metricPrefix) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.metricPrefix = (metricPrefix == null || metricPrefix.isEmpty())
                ? "flexretry"
                : metricPrefix;
    }

    @Override
    public void onScheduled(RetryContext<T> context, int attempt, Duration nextDelay) {
        Timer.builder(metricPrefix + ".scheduled")
                .tag("context", safeContext(context))
                .register(registry)
                .record(nextDelay);
    }

    @Override
    public void onAttempt(RetryContext<T> context, int attempt) {
        registry.counter(metricPrefix + ".attempts", "context", safeContext(context))
                .increment();
    }

    @Override
    public void onSuccess(RetryContext<T> context, int attempt, Duration elapsed) {
        Timer.builder(metricPrefix + ".success")
                .tag("context", safeContext(context))
                .register(registry)
                .record(elapsed);
    }

    @Override
    public void onFailure(RetryContext<T> context, int attempt, Throwable error, Duration elapsed) {
        Timer.builder(metricPrefix + ".failure")
                .tag("context", safeContext(context))
                .tag("error", safeError(error))
                .register(registry)
                .record(elapsed);
    }

    @Override
    public void onGiveUp(RetryContext<T> context, int attempt, Throwable error, Duration elapsed) {
        Timer.builder(metricPrefix + ".giveup")
                .tag("context", safeContext(context))
                .tag("error", safeError(error))
                .register(registry)
                .record(elapsed);
    }

    private String safeContext(RetryContext<T> context) {
        return context != null
                ? context.toString()
                : "unknown";
    }

    private String safeError(Throwable error) {
        return error != null
                ? error.getClass().getSimpleName()
                : "none";
    }
}
