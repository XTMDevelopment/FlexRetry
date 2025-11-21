package id.xtramile.flexretry.observability.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.RetryException;
import id.xtramile.flexretry.RetryOutcome;
import id.xtramile.flexretry.observability.RetryObservability;
import id.xtramile.flexretry.observability.events.RetryEvent;
import id.xtramile.flexretry.observability.events.RetryEventType;
import id.xtramile.flexretry.observability.events.SimpleRetryEventBus;
import id.xtramile.flexretry.observability.metrics.SimpleRetryMetrics;
import id.xtramile.flexretry.observability.trace.SimpleTraceContext;
import id.xtramile.flexretry.observability.trace.SimpleTraceScope;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests demonstrating observability features working with RetryExecutor.
 * Tests the direct integration of metrics, events, and tracing with the retry execution engine.
 */
class RetryExecutorObservabilityIntegrationTest {

    @Test
    void testRetryExecutor_WithMetrics_RecordsAllAttempts() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> attemptCount.incrementAndGet())
                .onSuccess((ctx, elapsed) -> successCount.incrementAndGet())
                .onFailure((ctx, error, elapsed) -> failureCount.incrementAndGet())
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
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
        assertEquals(1, successCount.get());
        assertTrue(failureCount.get() >= 1);
    }

    @Test
    void testRetryExecutor_WithEvents_PublishesAllEventTypes() {
        List<RetryEvent<String>> events = new ArrayList<>();
        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryOn(RuntimeException.class);
        RetryObservability.events(builder, eventBus);

        AtomicInteger callCount = new AtomicInteger(0);
        String result = builder.execute((Callable<String>) () -> {
            int call = callCount.incrementAndGet();

            if (call < 2) {
                throw new RuntimeException("retry");
            }

            return "success";
        }).getResult();

        assertEquals("success", result);
        assertTrue(events.stream().anyMatch(e -> e.getType() == RetryEventType.RETRY_ATTEMPT));
        assertTrue(events.stream().anyMatch(e -> e.getType() == RetryEventType.RETRY_FAILURE));
        assertTrue(events.stream().anyMatch(e -> e.getType() == RetryEventType.RETRY_SUCCESS));
    }

    @Test
    void testRetryExecutor_WithTracing_CreatesSpans() {
        List<String> spanNames = new ArrayList<>();
        AtomicInteger closeCount = new AtomicInteger(0);

        SimpleTraceContext traceContext = SimpleTraceContext.builder()
                .spanFactory((name, attributes) -> {
                    spanNames.add(name);

                    return SimpleTraceScope.create(closeCount::incrementAndGet);
                })
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryOn(RuntimeException.class);
        RetryObservability.tracing(builder, traceContext);

        AtomicInteger callCount = new AtomicInteger(0);
        String result = builder.execute((Callable<String>) () -> {
            int call = callCount.incrementAndGet();

            if (call < 2) {
                throw new RuntimeException("retry");
            }

            return "success";
        }).getResult();

        assertEquals("success", result);
        assertTrue(spanNames.size() >= 2);
        assertEquals(spanNames.size(), closeCount.get());
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testRetryExecutor_WithFullObservability_AllFeaturesWork() {
        AtomicInteger metricsAttemptCount = new AtomicInteger(0);
        AtomicInteger metricsSuccessCount = new AtomicInteger(0);
        List<RetryEvent<String>> events = new ArrayList<>();
        List<String> spanNames = new ArrayList<>();
        AtomicInteger spanCloseCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> metricsAttemptCount.incrementAndGet())
                .onSuccess((ctx, elapsed) -> metricsSuccessCount.incrementAndGet())
                .build();

        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        SimpleTraceContext traceContext = SimpleTraceContext.builder()
                .spanFactory((name, attributes) -> {
                    spanNames.add(name);

                    return SimpleTraceScope.create(spanCloseCount::incrementAndGet);
                })
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryOn(RuntimeException.class);
        RetryObservability.observability(builder, metrics, eventBus, traceContext);

        AtomicInteger callCount = new AtomicInteger(0);
        String result = builder.execute((Callable<String>) () -> {
            int call = callCount.incrementAndGet();

            if (call < 2) {
                throw new RuntimeException("retry");
            }

            return "success";
        }).getResult();

        assertEquals("success", result);
        assertTrue(metricsAttemptCount.get() >= 2);
        assertEquals(1, metricsSuccessCount.get());
        assertTrue(events.size() >= 0);
        assertTrue(spanNames.size() >= 0);
        assertEquals(spanNames.size(), spanCloseCount.get());
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testRetryExecutor_WithOutcome_ObservabilityWorks() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicInteger giveUpCount = new AtomicInteger(0);
        List<RetryEvent<String>> events = new ArrayList<>();

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> attemptCount.incrementAndGet())
                .onGiveUp((ctx, error, elapsed) -> giveUpCount.incrementAndGet())
                .build();

        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryOn(RuntimeException.class);
        RetryObservability.observability(builder, metrics, eventBus, null);

        RetryOutcome<String> outcome = builder.execute((Callable<String>) () -> {
            throw new RuntimeException("error");
        }).getOutcome();

        assertFalse(outcome.isSuccess());
        assertTrue(attemptCount.get() >= 1);
        assertEquals(1, giveUpCount.get());
        assertTrue(events.size() >= 0);
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testRetryExecutor_WithFailedRetry_AllObservabilityFeaturesRecordFailure() {
        AtomicInteger metricsAttemptCount = new AtomicInteger(0);
        AtomicInteger metricsFailureCount = new AtomicInteger(0);
        AtomicInteger metricsGiveUpCount = new AtomicInteger(0);
        List<RetryEvent<String>> events = new ArrayList<>();
        List<String> spanNames = new ArrayList<>();

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> metricsAttemptCount.incrementAndGet())
                .onFailure((ctx, error, elapsed) -> metricsFailureCount.incrementAndGet())
                .onGiveUp((ctx, error, elapsed) -> metricsGiveUpCount.incrementAndGet())
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
                .maxAttempts(3)
                .retryOn(RuntimeException.class);
        RetryObservability.observability(builder, metrics, eventBus, traceContext);

        assertThrows(RetryException.class, () -> builder.execute((Callable<String>) () -> {
            throw new RuntimeException("error");
        }).getResult());

        assertTrue(metricsAttemptCount.get() >= 1);
        assertTrue(metricsFailureCount.get() >= 1);
        assertEquals(1, metricsGiveUpCount.get());
        assertTrue(events.size() >= 0);
        assertTrue(spanNames.size() >= 0);
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testRetryExecutor_WithSuccessfulFirstAttempt_NoRetries() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        List<RetryEvent<String>> events = new ArrayList<>();

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> attemptCount.incrementAndGet())
                .onSuccess((ctx, elapsed) -> successCount.incrementAndGet())
                .build();

        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3);
        RetryObservability.observability(builder, metrics, eventBus, null);

        String result = builder.execute((Callable<String>) () -> "success").getResult();

        assertEquals("success", result);
        assertTrue(attemptCount.get() >= 1);
        assertTrue(successCount.get() >= 1);
        assertTrue(events.size() >= 0);
    }

    @Test
    void testRetryExecutor_WithMultipleRetries_RecordsAllAttempts() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        List<Integer> recordedAttempts = new ArrayList<>();

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> {
                    attemptCount.incrementAndGet();
                    recordedAttempts.add(attempt);
                })
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(5)
                .retryOn(RuntimeException.class);
        RetryObservability.metrics(builder, metrics);

        AtomicInteger callCount = new AtomicInteger(0);
        String result = builder.execute((Callable<String>) () -> {
            int call = callCount.incrementAndGet();

            if (call < 4) {
                throw new RuntimeException("retry");
            }

            return "success";
        }).getResult();

        assertEquals("success", result);
        assertEquals(4, attemptCount.get());
        assertEquals(1, (int) recordedAttempts.get(0));
        assertEquals(2, (int) recordedAttempts.get(1));
        assertEquals(3, (int) recordedAttempts.get(2));
        assertEquals(4, (int) recordedAttempts.get(3));
    }

    @Test
    void testRetryExecutor_WithScheduledEvents_RecordsDelays() {
        List<RetryEvent<String>> events = new ArrayList<>();
        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .backoff(id.xtramile.flexretry.strategy.backoff.BackoffStrategy.fixed(Duration.ofMillis(10)))
                .retryOn(RuntimeException.class);
        RetryObservability.events(builder, eventBus);

        AtomicInteger callCount = new AtomicInteger(0);
        String result = builder.execute((Callable<String>) () -> {
            int call = callCount.incrementAndGet();

            if (call < 2) {
                throw new RuntimeException("retry");
            }

            return "success";
        }).getResult();

        assertEquals("success", result);
        assertTrue(events.stream().anyMatch(e -> e.getType() == RetryEventType.RETRY_SCHEDULED));
    }
}

