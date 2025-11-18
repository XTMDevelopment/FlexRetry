package id.xtramile.flexretry.observability.simple;

import id.xtramile.flexretry.observability.events.RetryEventBus;
import id.xtramile.flexretry.observability.events.SimpleRetryEventBus;
import id.xtramile.flexretry.observability.metrics.CompositeRetryMetrics;
import id.xtramile.flexretry.observability.metrics.RetryMetrics;
import id.xtramile.flexretry.observability.simple.events.LoggingRetryEventListener;
import id.xtramile.flexretry.observability.simple.metrics.LoggingRetryMetrics;
import id.xtramile.flexretry.observability.simple.metrics.MicrometerRetryMetrics;
import id.xtramile.flexretry.observability.simple.trace.LoggingTraceContext;
import id.xtramile.flexretry.observability.trace.TraceContext;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;

public final class SimpleObservability<T> {
    private final RetryEventBus<T> eventBus;
    private final RetryMetrics<T> metrics;
    private final TraceContext traceContext;

    private SimpleObservability(RetryEventBus<T> eventBus, RetryMetrics<T> metrics, TraceContext traceContext) {
        this.eventBus = eventBus;
        this.metrics = metrics;
        this.traceContext = traceContext;
    }

    public RetryEventBus<T> getEventBus() {
        return eventBus;
    }

    public RetryMetrics<T> getMetrics() {
        return metrics;
    }

    public TraceContext getTraceContext() {
        return traceContext;
    }

    public static <T> SimpleObservability<T> create(Logger logger, MeterRegistry registry) {
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(registry, "registry");

        RetryMetrics<T> loggingMetrics = new LoggingRetryMetrics<>(logger);
        RetryMetrics<T> micrometerMetrics = new MicrometerRetryMetrics<>(registry, "flexretry");
        RetryMetrics<T> compositeMetrics = new CompositeRetryMetrics<>(List.of(loggingMetrics, micrometerMetrics));

        SimpleRetryEventBus<T> simpleRetryEventBus = SimpleRetryEventBus.create();
        simpleRetryEventBus.register(new LoggingRetryEventListener<>(logger));

        TraceContext loggingTraceContext = new LoggingTraceContext(logger);

        return new SimpleObservability<>(simpleRetryEventBus, compositeMetrics, loggingTraceContext);
    }
}
