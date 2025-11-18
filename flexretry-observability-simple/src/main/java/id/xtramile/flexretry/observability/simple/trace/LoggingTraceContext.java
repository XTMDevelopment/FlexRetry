package id.xtramile.flexretry.observability.simple.trace;

import id.xtramile.flexretry.observability.trace.TraceContext;
import id.xtramile.flexretry.observability.trace.TraceScope;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;

public final class LoggingTraceContext implements TraceContext {

    private final Logger logger;

    public LoggingTraceContext(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public TraceScope withSpan(String operationName, Map<String, String> attributes) {
        logger.debug("Trace span start: operation={} attributes={}", operationName, attributes);
        long start = System.nanoTime();

        return () -> {
            long durationNanos = System.nanoTime() - start;
            logger.debug("Trace span end: operation={} durationNanos={}", operationName, durationNanos);
        };
    }
}
