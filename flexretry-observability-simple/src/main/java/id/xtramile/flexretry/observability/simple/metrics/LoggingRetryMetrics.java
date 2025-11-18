package id.xtramile.flexretry.observability.simple.metrics;

import id.xtramile.flexretry.RetryContext;
import id.xtramile.flexretry.observability.metrics.RetryMetrics;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Objects;

public final class LoggingRetryMetrics<T> implements RetryMetrics<T> {

    private final Logger logger;

    public LoggingRetryMetrics(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void onScheduled(RetryContext<T> context, int attempt, Duration nextDelay) {
        logger.debug(
                "Metrics scheduled: attempt={} delay={} context={}",
                attempt,
                nextDelay,
                safeContext(context)
        );
    }

    @Override
    public void onAttempt(RetryContext<T> context, int attempt) {
        logger.debug(
                "Metrics attempt: attempt={} context={}",
                attempt,
                safeContext(context)
        );
    }

    @Override
    public void onSuccess(RetryContext<T> context, int attempt, Duration elapsed) {
        logger.info(
                "Metrics success: attempt={} elapsed={} context={}",
                attempt,
                elapsed,
                safeContext(context)
        );
    }

    @Override
    public void onFailure(RetryContext<T> context, int attempt, Throwable error, Duration elapsed) {
        logger.warn(
                "Metrics failure: attempt={} elapsed={} context={}",
                attempt,
                elapsed,
                safeContext(context),
                error
        );
    }

    @Override
    public void onGiveUp(RetryContext<T> context, int attempt, Throwable error, Duration elapsed) {
        logger.error(
                "Metrics give up: attempts={} elapsed={} context={}",
                attempt,
                elapsed,
                safeContext(context),
                error
        );
    }

    private String safeContext(RetryContext<T> context) {
        return context != null
                ? context.toString()
                : "null";
    }
}
