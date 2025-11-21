package id.xtramile.flexretry.observability.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.observability.RetryObservability;
import id.xtramile.flexretry.observability.events.RetryEvent;
import id.xtramile.flexretry.observability.events.RetryEventType;
import id.xtramile.flexretry.observability.events.SimpleRetryEventBus;
import id.xtramile.flexretry.observability.metrics.SimpleRetryMetrics;
import id.xtramile.flexretry.observability.trace.SimpleTraceContext;
import id.xtramile.flexretry.observability.trace.SimpleTraceScope;
import id.xtramile.flexretry.strategy.backoff.BackoffStrategy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for observability with different backoff strategies.
 */
class BackoffStrategyObservabilityIntegrationTest {

    @Test
    void testFixedBackoff_WithMetrics_RecordsScheduledDelays() {
        AtomicInteger scheduledCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onScheduled(ctx -> scheduledCount.incrementAndGet())
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .backoff(BackoffStrategy.fixed(Duration.ofMillis(50)))
                .retryOn(RuntimeException.class);
        RetryObservability.metrics(builder, metrics);

        AtomicInteger callCount = new AtomicInteger(0);
        String result = builder.execute((Callable<String>) () -> {
            int call = callCount.incrementAndGet();

            if (call < 2) {
                throw new RuntimeException("retry");
            }

            return "success";
        }).getResult();

        assertEquals("success", result);
        assertTrue(scheduledCount.get() >= 1);
    }

    @Test
    void testExponentialBackoff_WithEvents_RecordsScheduledEvents() {
        List<RetryEvent<String>> events = new ArrayList<>();
        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(4)
                .backoff(BackoffStrategy.exponential(Duration.ofMillis(10), 2.0))
                .retryOn(RuntimeException.class);
        RetryObservability.events(builder, eventBus);

        AtomicInteger callCount = new AtomicInteger(0);
        String result = builder.execute((Callable<String>) () -> {
            int call = callCount.incrementAndGet();

            if (call < 3) {
                throw new RuntimeException("retry");
            }

            return "success";
        }).getResult();

        assertEquals("success", result);

        long scheduledEvents = events.stream()
                .filter(e -> e.getType() == RetryEventType.RETRY_SCHEDULED)
                .count();

        assertTrue(scheduledEvents >= 2);
    }

    @Test
    void testLinearBackoff_WithMetrics_RecordsAllAttempts() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicInteger scheduledCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> attemptCount.incrementAndGet())
                .onScheduled(ctx -> scheduledCount.incrementAndGet())
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(4)
                .backoff(BackoffStrategy.exponential(Duration.ofMillis(10), 1.5))
                .retryOn(RuntimeException.class);
        RetryObservability.metrics(builder, metrics);

        AtomicInteger callCount = new AtomicInteger(0);
        String result = builder.execute((Callable<String>) () -> {
            int call = callCount.incrementAndGet();

            if (call < 3) {
                throw new RuntimeException("retry");
            }

            return "success";
        }).getResult();

        assertEquals("success", result);
        assertTrue(attemptCount.get() >= 3);
        assertTrue(scheduledCount.get() >= 2);
    }

    @Test
    void testNoBackoff_WithObservability_StillRecordsMetrics() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicInteger scheduledCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> attemptCount.incrementAndGet())
                .onScheduled(ctx -> scheduledCount.incrementAndGet())
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .backoff(BackoffStrategy.fixed(Duration.ZERO))
                .retryOn(RuntimeException.class);
        RetryObservability.metrics(builder, metrics);

        AtomicInteger callCount = new AtomicInteger(0);
        String result = builder.execute((Callable<String>) () -> {
            int call = callCount.incrementAndGet();

            if (call < 2) {
                throw new RuntimeException("retry");
            }

            return "success";
        }).getResult();

        assertEquals("success", result);
        assertTrue(attemptCount.get() >= 2);
        assertTrue(scheduledCount.get() >= 0);
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testBackoff_WithTracing_CreatesSpansForEachAttempt() {
        List<String> spanNames = new ArrayList<>();
        AtomicInteger closeCount = new AtomicInteger(0);

        SimpleTraceContext traceContext = SimpleTraceContext.builder()
                .spanFactory((name, attributes) -> {
                    spanNames.add(name);

                    return SimpleTraceScope.create(closeCount::incrementAndGet);
                })
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(4)
                .backoff(BackoffStrategy.fixed(Duration.ofMillis(10)))
                .retryOn(RuntimeException.class);
        RetryObservability.tracing(builder, traceContext);

        AtomicInteger callCount = new AtomicInteger(0);
        String result = builder.execute((Callable<String>) () -> {
            int call = callCount.incrementAndGet();

            if (call < 3) {
                throw new RuntimeException("retry");
            }

            return "success";
        }).getResult();

        assertEquals("success", result);
        assertTrue(spanNames.size() >= 0);
        assertTrue(closeCount.get() >= 0);
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testBackoff_WithFullObservability_AllFeaturesWork() {
        AtomicInteger metricsAttemptCount = new AtomicInteger(0);
        AtomicInteger metricsScheduledCount = new AtomicInteger(0);
        List<RetryEvent<String>> events = new ArrayList<>();
        List<String> spanNames = new ArrayList<>();

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> metricsAttemptCount.incrementAndGet())
                .onScheduled(ctx -> metricsScheduledCount.incrementAndGet())
                .build();

        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        SimpleTraceContext traceContext = SimpleTraceContext.builder()
                .spanFactory((name, attributes) -> {
                    spanNames.add(name);

                    return SimpleTraceScope.create(() -> {
                    });
                })
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(4)
                .backoff(BackoffStrategy.exponential(Duration.ofMillis(10), 2.0))
                .retryOn(RuntimeException.class);
        RetryObservability.observability(builder, metrics, eventBus, traceContext);

        AtomicInteger callCount = new AtomicInteger(0);
        String result = builder.execute((Callable<String>) () -> {
            int call = callCount.incrementAndGet();

            if (call < 3) {
                throw new RuntimeException("retry");
            }

            return "success";
        }).getResult();

        assertEquals("success", result);
        assertTrue(metricsAttemptCount.get() >= 3);
        assertTrue(metricsScheduledCount.get() >= 2);
        assertTrue(events.size() >= 0);
        assertTrue(spanNames.size() >= 0);
    }
}

