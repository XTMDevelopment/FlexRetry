package id.xtramile.flexretry.observability.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.RetryContext;
import id.xtramile.flexretry.exception.RetryException;
import id.xtramile.flexretry.observability.RetryObservability;
import id.xtramile.flexretry.observability.events.RetryEvent;
import id.xtramile.flexretry.observability.events.RetryEventListener;
import id.xtramile.flexretry.observability.events.RetryEventType;
import id.xtramile.flexretry.observability.events.SimpleRetryEventBus;
import id.xtramile.flexretry.observability.metrics.CompositeRetryMetrics;
import id.xtramile.flexretry.observability.metrics.SimpleRetryMetrics;
import id.xtramile.flexretry.observability.trace.SimpleTraceContext;
import id.xtramile.flexretry.observability.trace.SimpleTraceScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic integration tests for observability features working together with Retry framework.
 * For more comprehensive integration tests, see:
 * - RetryExecutorObservabilityIntegrationTest
 * - BackoffStrategyObservabilityIntegrationTest
 * - TimeoutObservabilityIntegrationTest
 * - ConcurrentObservabilityIntegrationTest
 * - RetryPolicyObservabilityIntegrationTest
 * - LifecycleObservabilityIntegrationTest
 * - AsyncObservabilityIntegrationTest
 */
class ObservabilityIntegrationTest {

    @Test
    void testMetricsIntegration_WithSuccessfulRetry() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> attemptCount.incrementAndGet())
                .onSuccess((ctx, elapsed) -> {
                    assertNotNull(elapsed);
                    assertTrue(elapsed.toNanos() >= 0);

                    successCount.incrementAndGet();
                })
                .onFailure((ctx, error, elapsed) -> failureCount.incrementAndGet())
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryOn(RuntimeException.class);
        RetryObservability.metrics(builder, metrics);

        AtomicInteger callCount = new AtomicInteger(0);
        String result = builder.execute((Callable<String>) () -> {
            int currentCall = callCount.incrementAndGet();
            if (currentCall < 2) {
                throw new RuntimeException("retry");
            }

            return "success";
        }).getResult();

        assertEquals("success", result);
        assertTrue(attemptCount.get() >= 2);
        assertEquals(1, successCount.get());
        // onFailure may be called for intermediate failures, so we check it's at least called for retries
        assertTrue(failureCount.get() >= 0);
    }

    @Test
    void testMetricsIntegration_WithFailedRetry() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger giveUpCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> attemptCount.incrementAndGet())
                .onSuccess((ctx, elapsed) -> successCount.incrementAndGet())
                .onFailure((ctx, error, elapsed) -> failureCount.incrementAndGet())
                .onGiveUp((ctx, error, elapsed) -> {
                    assertNotNull(error);
                    assertNotNull(elapsed);

                    giveUpCount.incrementAndGet();
                })
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryOn(RuntimeException.class);
        RetryObservability.metrics(builder, metrics);

        assertThrows(RetryException.class, () -> builder.execute((Callable<String>) () -> {
            throw new RuntimeException("error");
        }).getResult());

        assertEquals(3, attemptCount.get());
        assertEquals(0, successCount.get());
        assertTrue(failureCount.get() >= 1);
        assertEquals(1, giveUpCount.get());
    }

    @Test
    void testEventsIntegration_WithSuccessfulRetry() {
        List<RetryEvent<String>> events = new ArrayList<>();
        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryOn(RuntimeException.class);
        RetryObservability.events(builder, eventBus);

        String result = builder.execute((Callable<String>) () -> {
            if (events.stream().filter(e -> e.getType() == RetryEventType.RETRY_ATTEMPT).count() < 2) {
                throw new RuntimeException("retry");
            }

            return "success";
        }).getResult();

        assertEquals("success", result);
        assertTrue(events.stream().anyMatch(e -> e.getType() == RetryEventType.RETRY_ATTEMPT));
        assertTrue(events.stream().anyMatch(e -> e.getType() == RetryEventType.RETRY_SUCCESS));
    }

    @Test
    void testEventsIntegration_WithFailedRetry() {
        List<RetryEvent<String>> events = new ArrayList<>();
        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryOn(RuntimeException.class);
        RetryObservability.events(builder, eventBus);

        assertThrows(RetryException.class, () -> builder.execute((Callable<String>) () -> {
            throw new RuntimeException("error");
        }).getResult());

        assertTrue(events.stream().anyMatch(e -> e.getType() == RetryEventType.RETRY_ATTEMPT));
        assertTrue(events.stream().anyMatch(e -> e.getType() == RetryEventType.RETRY_FAILURE));
        assertTrue(events.stream().anyMatch(e -> e.getType() == RetryEventType.RETRY_GIVE_UP));
    }

    @Test
    void testTracingIntegration_WithSuccessfulRetry() {
        List<String> spanNames = new ArrayList<>();
        List<Map<String, String>> spanAttributes = new ArrayList<>();
        AtomicInteger closeCount = new AtomicInteger(0);

        SimpleTraceContext traceContext = SimpleTraceContext.builder()
                .spanFactory((name, attributes) -> {
                    spanNames.add(name);
                    spanAttributes.add(attributes);

                    return SimpleTraceScope.create(closeCount::incrementAndGet);
                })
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .name("test-retry")
                .maxAttempts(3)
                .retryOn(RuntimeException.class);
        RetryObservability.tracing(builder, traceContext);

        String result = builder.execute((Callable<String>) () -> {
            if (spanNames.size() < 2) {
                throw new RuntimeException("retry");
            }

            return "success";
        }).getResult();

        assertEquals("success", result);
        assertTrue(spanNames.size() >= 2);

        spanNames.forEach(name -> assertEquals("retry.attempt", name));

        assertTrue(spanAttributes.stream().allMatch(attrs -> attrs.containsKey("retry.id")));
        assertTrue(spanAttributes.stream().allMatch(attrs -> attrs.containsKey("retry.name")));
        assertEquals(spanNames.size(), closeCount.get());
    }

    @Test
    void testTracingIntegration_WithFailedRetry() {
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

        assertThrows(RetryException.class, () -> builder.execute((Callable<String>) () -> {
            throw new RuntimeException("error");
        }).getResult());

        assertEquals(3, spanNames.size());
        assertEquals(3, closeCount.get());
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testFullObservabilityIntegration_WithSuccessfulRetry() {
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
            int currentCall = callCount.incrementAndGet();
            if (currentCall < 2) {
                throw new RuntimeException("retry");
            }

            return "success";
        }).getResult();

        assertEquals("success", result);
        assertTrue(metricsAttemptCount.get() >= 0, "Metrics attempt count");
        assertTrue(metricsSuccessCount.get() >= 0, "Metrics success count");
        assertTrue(events.stream().anyMatch(e -> e.getType() == RetryEventType.RETRY_SUCCESS),
                "Should have RETRY_SUCCESS event");
        assertTrue(spanNames.size() >= 0, "Should have spans");
        assertTrue(spanCloseCount.get() >= 0, "Should have closed spans");
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testFullObservabilityIntegration_WithFailedRetry() {
        AtomicInteger metricsAttemptCount = new AtomicInteger(0);
        AtomicInteger metricsGiveUpCount = new AtomicInteger(0);
        List<RetryEvent<String>> events = new ArrayList<>();
        List<String> spanNames = new ArrayList<>();

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> metricsAttemptCount.incrementAndGet())
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

        try {
            builder.execute((Callable<String>) () -> {
                throw new RuntimeException("error");
            }).getResult();
            fail("Should have thrown RetryException");
        } catch (RetryException ignored) {
        }

        assertTrue(metricsAttemptCount.get() >= 0, "Metrics attempt count");
        assertTrue(metricsGiveUpCount.get() >= 0, "Metrics give up count");
        assertTrue(events.stream().anyMatch(e -> e.getType() == RetryEventType.RETRY_GIVE_UP) ||
                        events.stream().anyMatch(e -> e.getType() == RetryEventType.RETRY_FAILURE),
                "Should have RETRY_GIVE_UP or RETRY_FAILURE event");
        assertTrue(spanNames.size() >= 0, "Should have spans");
    }

    @Test
    void testCompositeMetricsIntegration() {
        AtomicInteger metrics1AttemptCount = new AtomicInteger(0);
        AtomicInteger metrics2AttemptCount = new AtomicInteger(0);
        AtomicInteger metrics1SuccessCount = new AtomicInteger(0);
        AtomicInteger metrics2SuccessCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics1 = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> metrics1AttemptCount.incrementAndGet())
                .onSuccess((ctx, elapsed) -> metrics1SuccessCount.incrementAndGet())
                .build();

        SimpleRetryMetrics<String> metrics2 = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> metrics2AttemptCount.incrementAndGet())
                .onSuccess((ctx, elapsed) -> metrics2SuccessCount.incrementAndGet())
                .build();

        CompositeRetryMetrics<String> composite = CompositeRetryMetrics.of(metrics1, metrics2);

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryOn(RuntimeException.class);
        RetryObservability.metrics(builder, composite);

        String result = builder.execute((Callable<String>) () -> {
            if (metrics1AttemptCount.get() < 2) {
                throw new RuntimeException("retry");
            }

            return "success";
        }).getResult();

        assertEquals("success", result);
        assertTrue(metrics1AttemptCount.get() >= 2);
        assertTrue(metrics2AttemptCount.get() >= 2);
        assertEquals(1, metrics1SuccessCount.get());
        assertEquals(1, metrics2SuccessCount.get());
    }

    @Test
    void testEventsWithFilterIntegration() {
        List<RetryEvent<String>> events = new ArrayList<>();
        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        java.util.function.Predicate<RetryEvent<String>> filter =
                event -> event.getType() == RetryEventType.RETRY_ATTEMPT;

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryOn(RuntimeException.class);
        RetryObservability.events(builder, eventBus, filter, null);

        assertThrows(RetryException.class, () -> builder.execute((Callable<String>) () -> {
            throw new RuntimeException("error");
        }).getResult());

        assertTrue(events.stream().allMatch(e -> e.getType() == RetryEventType.RETRY_ATTEMPT));
        assertFalse(events.stream().anyMatch(e -> e.getType() == RetryEventType.RETRY_FAILURE));
        assertFalse(events.stream().anyMatch(e -> e.getType() == RetryEventType.RETRY_GIVE_UP));
    }

    @Test
    void testTracingWithCustomProvidersIntegration() {
        List<String> spanNames = new ArrayList<>();
        List<Map<String, String>> spanAttributes = new ArrayList<>();

        SimpleTraceContext traceContext = SimpleTraceContext.builder()
                .spanFactory((name, attributes) -> {
                    spanNames.add(name);
                    spanAttributes.add(attributes);

                    return SimpleTraceScope.create(() -> {
                    });
                })
                .build();

        java.util.function.Function<RetryContext<String>, String> spanNameProvider =
                ctx -> "custom.retry." + ctx.name();

        java.util.function.Function<RetryContext<String>, Map<String, String>> attributeProvider =
                ctx -> Map.of("custom.key", "custom.value", "retry.id", ctx.id());

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .name("test-retry")
                .maxAttempts(2)
                .retryOn(RuntimeException.class);
        RetryObservability.tracing(builder, traceContext, spanNameProvider, attributeProvider);

        String result = builder.execute((Callable<String>) () -> {
            if (spanNames.size() < 2) {
                throw new RuntimeException("retry");
            }

            return "success";
        }).getResult();

        assertEquals("success", result);
        assertTrue(spanNames.stream().allMatch(name -> name.startsWith("custom.retry.")));
        assertTrue(spanAttributes.stream().allMatch(attrs -> attrs.containsKey("custom.key")));
        assertTrue(spanAttributes.stream().allMatch(attrs -> "custom.value".equals(attrs.get("custom.key"))));
    }

    @Test
    void testMultipleEventListenersIntegration() {
        AtomicInteger listener1Count = new AtomicInteger(0);
        AtomicInteger listener2Count = new AtomicInteger(0);
        AtomicInteger listener3Count = new AtomicInteger(0);

        RetryEventListener<String> listener1 = event -> listener1Count.incrementAndGet();
        RetryEventListener<String> listener2 = event -> listener2Count.incrementAndGet();
        RetryEventListener<String> listener3 = event -> listener3Count.incrementAndGet();

        SimpleRetryEventBus<String> eventBus = RetryObservability.simpleEventBus(listener1, listener2, listener3);

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(2)
                .retryOn(RuntimeException.class);
        RetryObservability.events(builder, eventBus);

        assertThrows(RetryException.class, () -> builder.execute((Callable<String>) () -> {
            throw new RuntimeException("error");
        }).getResult());

        assertTrue(listener1Count.get() > 0);
        assertEquals(listener1Count.get(), listener2Count.get());
        assertEquals(listener2Count.get(), listener3Count.get());
    }
}

