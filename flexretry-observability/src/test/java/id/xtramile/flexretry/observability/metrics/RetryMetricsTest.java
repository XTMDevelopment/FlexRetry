package id.xtramile.flexretry.observability.metrics;

import id.xtramile.flexretry.RetryContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryMetricsTest {

    @Test
    void testNoop_ReturnsNoopInstance() {
        RetryMetrics<String> noop1 = RetryMetrics.noop();
        RetryMetrics<String> noop2 = RetryMetrics.noop();

        assertNotNull(noop1);
        assertNotNull(noop2);
        assertSame(noop1.getClass(), noop2.getClass());
    }

    @Test
    void testNoop_WithDifferentTypes_ReturnsNoopInstance() {
        RetryMetrics<String> noopString = RetryMetrics.noop();
        RetryMetrics<Integer> noopInt = RetryMetrics.noop();
        RetryMetrics<Object> noopObject = RetryMetrics.noop();

        assertNotNull(noopString);
        assertNotNull(noopInt);
        assertNotNull(noopObject);
    }

    @Test
    void testDefaultMethods_DoNothing() {
        RetryMetrics<String> metrics = new RetryMetrics<>() {
        };

        RetryContext<String> context = new RetryContext<>(
                "test-id", "test-name", 1, 3, null, null, Duration.ZERO, null);
        RuntimeException error = new RuntimeException("test error");

        assertDoesNotThrow(() -> metrics.onScheduled(context, 1, Duration.ofMillis(100)));
        assertDoesNotThrow(() -> metrics.onAttempt(context, 1));
        assertDoesNotThrow(() -> metrics.onSuccess(context, 1, Duration.ofMillis(150)));
        assertDoesNotThrow(() -> metrics.onFailure(context, 1, error, Duration.ofMillis(200)));
        assertDoesNotThrow(() -> metrics.onGiveUp(context, 1, error, Duration.ofMillis(300)));
    }

    @Test
    void testDefaultMethods_WithNullParameters() {
        RetryMetrics<String> metrics = new RetryMetrics<>() {
        };

        RuntimeException error = new RuntimeException("test error");

        assertDoesNotThrow(() -> metrics.onScheduled(null, 1, Duration.ofMillis(100)));
        assertDoesNotThrow(() -> metrics.onAttempt(null, 1));
        assertDoesNotThrow(() -> metrics.onSuccess(null, 1, Duration.ofMillis(150)));
        assertDoesNotThrow(() -> metrics.onFailure(null, 1, error, Duration.ofMillis(200)));
        assertDoesNotThrow(() -> metrics.onGiveUp(null, 1, error, Duration.ofMillis(300)));
        assertDoesNotThrow(() -> metrics.onFailure(null, 1, null, Duration.ofMillis(200)));
        assertDoesNotThrow(() -> metrics.onGiveUp(null, 1, null, Duration.ofMillis(300)));
    }

    @Test
    void testCustomImplementation_OverridesDefaults() {
        AtomicInteger callCount = new AtomicInteger(0);

        RetryMetrics<String> metrics = new RetryMetrics<>() {
            @Override
            public void onAttempt(RetryContext<String> context, int attempt) {
                callCount.incrementAndGet();
            }
        };

        RetryContext<String> context = new RetryContext<>(
                "test-id", "test-name", 1, 3, null, null, Duration.ZERO, null);

        metrics.onAttempt(context, 1);

        assertEquals(1, callCount.get());
    }
}

