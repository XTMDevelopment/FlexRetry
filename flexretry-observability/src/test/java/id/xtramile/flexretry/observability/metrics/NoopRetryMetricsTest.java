package id.xtramile.flexretry.observability.metrics;

import id.xtramile.flexretry.RetryContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;

class NoopRetryMetricsTest {

    @Test
    void testInstance_IsSingleton() {
        NoopRetryMetrics<?> instance1 = NoopRetryMetrics.INSTANCE;
        NoopRetryMetrics<?> instance2 = NoopRetryMetrics.INSTANCE;

        assertSame(instance1, instance2);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testOnScheduled_DoesNothing() {
        NoopRetryMetrics<String> metrics = (NoopRetryMetrics<String>) NoopRetryMetrics.INSTANCE;
        RetryContext<String> context = new RetryContext<>(
                "test-id", "test-name", 1, 3, null, null, Duration.ZERO, null);

        assertDoesNotThrow(() -> metrics.onScheduled(context, 1, Duration.ofMillis(100)));
        assertDoesNotThrow(() -> metrics.onScheduled(null, 1, Duration.ofMillis(100)));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testOnAttempt_DoesNothing() {
        NoopRetryMetrics<String> metrics = (NoopRetryMetrics<String>) NoopRetryMetrics.INSTANCE;
        RetryContext<String> context = new RetryContext<>(
                "test-id", "test-name", 1, 3, null, null, Duration.ZERO, null);

        assertDoesNotThrow(() -> metrics.onAttempt(context, 1));
        assertDoesNotThrow(() -> metrics.onAttempt(null, 1));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testOnSuccess_DoesNothing() {
        NoopRetryMetrics<String> metrics = (NoopRetryMetrics<String>) NoopRetryMetrics.INSTANCE;
        RetryContext<String> context = new RetryContext<>(
                "test-id", "test-name", 1, 3, "result", null, Duration.ZERO, null);

        assertDoesNotThrow(() -> metrics.onSuccess(context, 1, Duration.ofMillis(150)));
        assertDoesNotThrow(() -> metrics.onSuccess(null, 1, Duration.ofMillis(150)));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testOnFailure_DoesNothing() {
        NoopRetryMetrics<String> metrics = (NoopRetryMetrics<String>) NoopRetryMetrics.INSTANCE;
        RetryContext<String> context = new RetryContext<>(
                "test-id", "test-name", 1, 3, null, null, Duration.ZERO, null);
        RuntimeException error = new RuntimeException("test error");

        assertDoesNotThrow(() -> metrics.onFailure(context, 1, error, Duration.ofMillis(200)));
        assertDoesNotThrow(() -> metrics.onFailure(null, 1, error, Duration.ofMillis(200)));
        assertDoesNotThrow(() -> metrics.onFailure(context, 1, null, Duration.ofMillis(200)));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testOnGiveUp_DoesNothing() {
        NoopRetryMetrics<String> metrics = (NoopRetryMetrics<String>) NoopRetryMetrics.INSTANCE;
        RetryContext<String> context = new RetryContext<>(
                "test-id", "test-name", 3, 3, null, null, Duration.ZERO, null);
        RuntimeException error = new RuntimeException("test error");

        assertDoesNotThrow(() -> metrics.onGiveUp(context, 3, error, Duration.ofMillis(300)));
        assertDoesNotThrow(() -> metrics.onGiveUp(null, 3, error, Duration.ofMillis(300)));
        assertDoesNotThrow(() -> metrics.onGiveUp(context, 3, null, Duration.ofMillis(300)));
    }
}

