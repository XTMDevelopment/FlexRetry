package id.xtramile.flexretry.observability.simple.events;

import id.xtramile.flexretry.observability.events.RetryEvent;
import id.xtramile.flexretry.observability.events.RetryEventListener;
import id.xtramile.flexretry.observability.events.RetryEventType;
import org.slf4j.Logger;

import java.util.Objects;

public final class LoggingRetryEventListener<T> implements RetryEventListener<T> {

    private final Logger logger;

    public LoggingRetryEventListener(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void onEvent(RetryEvent<T> event) {
        if (event == null) {
            return;
        }

        RetryEventType type = event.getType();

        switch (type) {
            case RETRY_SCHEDULED: logScheduled(event); break;
            case RETRY_ATTEMPT: logAttempt(event); break;
            case RETRY_SUCCESS: logSuccess(event); break;
            case RETRY_FAILURE: logFailure(event); break;
            case RETRY_GIVE_UP: logGiveUp(event); break;
            default:
                logger.debug("Received unknown retry event: {}: ", event);
        }
    }

    private void logScheduled(RetryEvent<T> event) {
        logger.debug(
                "Retry scheduled: attempt={} nextDelay={} context={}",
                event.getAttempt(),
                event.getNextDelay(),
                safeContext(event)
        );
    }

    private void logAttempt(RetryEvent<T> event) {
        logger.debug(
                "Retry attempt: attempt={} context={}",
                event.getAttempt(),
                safeContext(event)
        );
    }

    private void logSuccess(RetryEvent<T> event) {
        logger.info(
                "Retry succeeded: attempt={} context={}",
                event.getAttempt(),
                safeContext(event)
        );
    }

    private void logFailure(RetryEvent<T> event) {
        logger.warn(
                "Retry attempt failed: attempt={} context={}",
                event.getAttempt(),
                safeContext(event),
                event.getLastError()
        );
    }

    private void logGiveUp(RetryEvent<T> event) {
        logger.error(
                "Retry give up: attempts={} context={}",
                event.getAttempt(),
                safeContext(event),
                event.getLastError()
        );
    }

    private String safeContext(RetryEvent<T> event) {
        if (event.getContext() == null) {
            return "null";
        }

        return event.getContext().toString();
    }
}
