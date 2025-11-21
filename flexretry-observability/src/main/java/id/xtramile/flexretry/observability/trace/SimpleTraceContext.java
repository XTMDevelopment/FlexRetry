package id.xtramile.flexretry.observability.trace;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Simple customizable trace context implementation that delegates to a span factory function.
 */
public final class SimpleTraceContext implements TraceContext {
    private final BiFunction<String, Map<String, String>, TraceScope> spanFactory;

    private SimpleTraceContext(BiFunction<String, Map<String, String>, TraceScope> spanFactory) {
        this.spanFactory = Objects.requireNonNull(spanFactory, "spanFactory");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SimpleTraceContext create(BiFunction<String, Map<String, String>, TraceScope> spanFactory) {
        return new SimpleTraceContext(spanFactory);
    }

    @Override
    public TraceScope withSpan(String operationName, Map<String, String> attributes) {
        return spanFactory.apply(operationName, attributes != null ? attributes : Collections.emptyMap());
    }

    public static final class Builder {
        private BiFunction<String, Map<String, String>, TraceScope> spanFactory;

        private Builder() {
        }

        public Builder spanFactory(BiFunction<String, Map<String, String>, TraceScope> spanFactory) {
            this.spanFactory = spanFactory;
            return this;
        }

        public SimpleTraceContext build() {
            Objects.requireNonNull(spanFactory, "spanFactory");
            return new SimpleTraceContext(spanFactory);
        }
    }
}

