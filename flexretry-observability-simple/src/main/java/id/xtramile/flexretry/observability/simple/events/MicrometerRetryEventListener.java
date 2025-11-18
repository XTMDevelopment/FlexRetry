package id.xtramile.flexretry.observability.simple.events;

import id.xtramile.flexretry.observability.events.RetryEvent;
import id.xtramile.flexretry.observability.events.RetryEventListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;

public final class MicrometerRetryEventListener<T> implements RetryEventListener<T> {

    private final MeterRegistry registry;
    private final String metricPrefix;

    public MicrometerRetryEventListener(MeterRegistry registry, String metricPrefix) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.metricPrefix = (metricPrefix == null || metricPrefix.isEmpty())
                ? "flexretry"
                : metricPrefix;
    }

    @Override
    public void onEvent(RetryEvent<T> event) {
        if (event == null) {
            return;
        }

        String contextTag = event.getContext() != null
                ? event.getContext().toString()
                : "unknown";

        String metricName = metricPrefix + ".events." + event.getType().name().toLowerCase();

        Counter.builder(metricName)
                .tag("context", contextTag)
                .register(registry)
                .increment();
    }
}
