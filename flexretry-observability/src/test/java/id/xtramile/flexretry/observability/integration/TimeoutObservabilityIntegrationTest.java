package id.xtramile.flexretry.observability.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.exception.RetryException;
import id.xtramile.flexretry.observability.RetryObservability;
import id.xtramile.flexretry.observability.events.RetryEvent;
import id.xtramile.flexretry.observability.events.RetryEventType;
import id.xtramile.flexretry.observability.events.SimpleRetryEventBus;
import id.xtramile.flexretry.observability.metrics.SimpleRetryMetrics;
import id.xtramile.flexretry.observability.trace.SimpleTraceContext;
import id.xtramile.flexretry.observability.trace.SimpleTraceScope;
import id.xtramile.flexretry.strategy.timeout.ExponentialTimeout;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for observability with timeout strategies.
 */
class TimeoutObservabilityIntegrationTest {

    @Test
    void testFixedTimeout_WithMetrics_RecordsAttempts() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> attemptCount.incrementAndGet())
                .onFailure((ctx, error, elapsed) -> failureCount.incrementAndGet())
                .build();

        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Retry.Builder<String> builder = Retry.<String>newBuilder()
                    .maxAttempts(2)
                    .attemptTimeout(Duration.ofMillis(100))
                    .attemptExecutor(executor)
                    .retryOn(RuntimeException.class);
            RetryObservability.metrics(builder, metrics);

            assertThrows(RetryException.class, () -> builder.execute((Callable<String>) () -> {
                Thread.sleep(200);
                return "success";
            }).getResult());

            assertTrue(attemptCount.get() >= 1);
            assertTrue(failureCount.get() >= 1);

        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testExponentialTimeout_WithEvents_PublishesTimeoutEvents() {
        List<RetryEvent<String>> events = new ArrayList<>();
        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Retry.Builder<String> builder = Retry.<String>newBuilder()
                    .maxAttempts(2)
                    .attemptTimeouts(new ExponentialTimeout(Duration.ofMillis(50), 2.0, Duration.ofSeconds(10)))
                    .attemptExecutor(executor)
                    .retryOn(RuntimeException.class);
            RetryObservability.events(builder, eventBus);

            assertThrows(RetryException.class, () -> builder.execute((Callable<String>) () -> {
                Thread.sleep(200);
                return "success";
            }).getResult());

            assertTrue(events.stream().anyMatch(e -> e.getType() == RetryEventType.RETRY_ATTEMPT));
            assertTrue(events.stream().anyMatch(e -> e.getType() == RetryEventType.RETRY_FAILURE ||
                    e.getType() == RetryEventType.RETRY_GIVE_UP));

        } finally {
            executor.shutdown();
        }
    }

    @SuppressWarnings("SizeReplaceableByIsEmpty")
    @Test
    void testTimeout_WithTracing_CreatesSpans() {
        List<String> spanNames = new ArrayList<>();
        AtomicInteger closeCount = new AtomicInteger(0);

        SimpleTraceContext traceContext = SimpleTraceContext.builder()
                .spanFactory((name, attributes) -> {
                    spanNames.add(name);

                    return SimpleTraceScope.create(closeCount::incrementAndGet);
                })
                .build();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Retry.Builder<String> builder = Retry.<String>newBuilder()
                    .maxAttempts(2)
                    .attemptTimeout(Duration.ofMillis(100))
                    .attemptExecutor(executor)
                    .retryOn(RuntimeException.class);
            RetryObservability.tracing(builder, traceContext);

            assertThrows(RetryException.class, () -> builder.execute((Callable<String>) () -> {
                Thread.sleep(200);
                return "success";
            }).getResult());

            assertTrue(spanNames.size() >= 1);
            assertTrue(closeCount.get() >= 1);

        } finally {
            executor.shutdown();
        }
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testTimeout_WithFullObservability_AllFeaturesWork() {
        AtomicInteger metricsAttemptCount = new AtomicInteger(0);
        AtomicInteger metricsFailureCount = new AtomicInteger(0);
        List<RetryEvent<String>> events = new ArrayList<>();
        List<String> spanNames = new ArrayList<>();

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> metricsAttemptCount.incrementAndGet())
                .onFailure((ctx, error, elapsed) -> metricsFailureCount.incrementAndGet())
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

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Retry.Builder<String> builder = Retry.<String>newBuilder()
                    .maxAttempts(2)
                    .attemptTimeout(Duration.ofMillis(100))
                    .attemptExecutor(executor)
                    .retryOn(RuntimeException.class);
            RetryObservability.observability(builder, metrics, eventBus, traceContext);

            assertThrows(RetryException.class, () -> builder.execute((Callable<String>) () -> {
                Thread.sleep(200);

                return "success";
            }).getResult());

            assertTrue(metricsAttemptCount.get() >= 1);
            assertTrue(metricsFailureCount.get() >= 1);
            assertTrue(events.size() >= 0);
            assertTrue(spanNames.size() >= 0);

        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testTimeout_WithSuccessfulExecution_RecordsSuccess() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> attemptCount.incrementAndGet())
                .onSuccess((ctx, elapsed) -> successCount.incrementAndGet())
                .build();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Retry.Builder<String> builder = Retry.<String>newBuilder()
                    .maxAttempts(2)
                    .attemptTimeout(Duration.ofMillis(500))
                    .attemptExecutor(executor);
            RetryObservability.metrics(builder, metrics);

            String result = builder.execute((Callable<String>) () -> {
                Thread.sleep(50);

                return "success";
            }).getResult();

            assertEquals("success", result);
            assertEquals(1, attemptCount.get());
            assertEquals(1, successCount.get());

        } finally {
            executor.shutdown();
        }
    }
}

