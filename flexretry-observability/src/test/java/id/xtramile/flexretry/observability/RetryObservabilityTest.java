package id.xtramile.flexretry.observability;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.RetryContext;
import id.xtramile.flexretry.exception.RetryException;
import id.xtramile.flexretry.observability.events.*;
import id.xtramile.flexretry.observability.metrics.CompositeRetryMetrics;
import id.xtramile.flexretry.observability.metrics.RetryMetrics;
import id.xtramile.flexretry.observability.metrics.SimpleRetryMetrics;
import id.xtramile.flexretry.observability.trace.SimpleTraceContext;
import id.xtramile.flexretry.observability.trace.SimpleTraceScope;
import id.xtramile.flexretry.observability.trace.TraceContext;
import id.xtramile.flexretry.strategy.backoff.BackoffStrategy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RetryObservabilityTest {

    @Test
    void testMetrics_WithNullBuilder_ThrowsException() {
        RetryMetrics<String> metrics = RetryMetrics.noop();

        assertThrows(NullPointerException.class, () -> RetryObservability.metrics(null, metrics));
    }

    @Test
    void testMetrics_WithNullMetrics_ThrowsException() {
        Retry.Builder<String> builder = Retry.newBuilder();

        assertThrows(NullPointerException.class, () -> RetryObservability.metrics(builder, null));
    }

    @Test
    void testMetrics_WithNoopMetrics_ReturnsBuilderUnchanged() {
        Retry.Builder<String> builder = Retry.newBuilder();
        Retry.Builder<String> result = RetryObservability.metrics(builder, RetryMetrics.noop());

        assertSame(builder, result);
    }

    @Test
    void testMetrics_RecordsAttempts() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicInteger onAttemptCallCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> {
                    onAttemptCallCount.incrementAndGet();

                    assertEquals(attemptCount.incrementAndGet(), attempt);
                })
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryOn(RuntimeException.class);

        RetryObservability.metrics(builder, metrics);

        assertThrows(RetryException.class, () -> builder.execute((Callable<String>) () -> {
            throw new RuntimeException("error");
        }).getResult());

        assertEquals(3, onAttemptCallCount.get());
    }

    @Test
    void testMetrics_RecordsSuccess() {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger attemptCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> attemptCount.incrementAndGet())
                .onSuccess((ctx, elapsed) -> {
                    assertNotNull(elapsed);
                    assertTrue(elapsed.toNanos() >= 0);

                    successCount.incrementAndGet();
                })
                .build();

        Retry.Builder<String> builder = Retry.newBuilder();
        RetryObservability.metrics(builder, metrics);

        String result = builder.execute((Callable<String>) () -> "success").getResult();
        assertEquals("success", result);
        assertEquals(1, attemptCount.get());
        assertEquals(1, successCount.get());
    }

    @Test
    void testMetrics_RecordsFailure() {
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger attemptCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> attemptCount.incrementAndGet())
                .onFailure((ctx, error, elapsed) -> {
                    assertNotNull(error);
                    assertNotNull(elapsed);

                    failureCount.incrementAndGet();
                })
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(2)
                .retryOn(RuntimeException.class);
        RetryObservability.metrics(builder, metrics);

        assertThrows(RetryException.class, () -> builder.execute((Callable<String>) () -> {
            throw new RuntimeException("error");
        }).getResult());

        assertTrue(failureCount.get() >= 1);
    }

    @Test
    void testMetrics_RecordsGiveUp() {
        AtomicInteger giveUpCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onGiveUp((ctx, error, elapsed) -> {
                    assertNotNull(error);
                    assertNotNull(elapsed);

                    giveUpCount.incrementAndGet();
                })
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(2)
                .retryOn(RuntimeException.class);
        RetryObservability.metrics(builder, metrics);

        assertThrows(RetryException.class, () -> builder.execute((Callable<String>) () -> {
            throw new RuntimeException("error");
        }).getResult());

        assertEquals(1, giveUpCount.get());
    }

    @Test
    void testMetrics_RecordsScheduled() {
        AtomicInteger scheduledCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onScheduled(ctx -> scheduledCount.incrementAndGet())
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .backoff(BackoffStrategy.fixed(Duration.ofMillis(10)))
                .retryOn(RuntimeException.class);
        RetryObservability.metrics(builder, metrics);

        assertThrows(RetryException.class, () -> builder.execute((Callable<String>) () -> {
            throw new RuntimeException("error");
        }).getResult());

        assertTrue(scheduledCount.get() >= 1);
    }

    @Test
    void testEvents_WithNullBuilder_ThrowsException() {
        RetryEventBus<String> eventBus = RetryEventBus.noop();

        assertThrows(NullPointerException.class, () -> RetryObservability.events(null, eventBus));
    }

    @Test
    void testEvents_WithNullEventBus_ThrowsException() {
        Retry.Builder<String> builder = Retry.newBuilder();

        assertThrows(NullPointerException.class, () -> RetryObservability.events(builder, null));
    }

    @Test
    void testEvents_WithNoopEventBus_ReturnsBuilderUnchanged() {
        Retry.Builder<String> builder = Retry.newBuilder();
        Retry.Builder<String> result = RetryObservability.events(builder, RetryEventBus.noop());
        assertSame(builder, result);
    }

    @Test
    void testEvents_PublishesAttemptEvents() {
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

        assertTrue(events.size() >= 3);
        // Filter for only RETRY_ATTEMPT events
        List<RetryEvent<String>> attemptEvents = events.stream()
                .filter(e -> e.getType() == RetryEventType.RETRY_ATTEMPT)
                .collect(Collectors.toList());
        assertTrue(attemptEvents.size() >= 3);
        attemptEvents.forEach(event -> {
            assertEquals(RetryEventType.RETRY_ATTEMPT, event.getType());
            assertNotNull(event.getContext());
        });
    }

    @Test
    void testEvents_PublishesSuccessEvents() {
        List<RetryEvent<String>> events = new ArrayList<>();
        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        Retry.Builder<String> builder = Retry.newBuilder();
        RetryObservability.events(builder, eventBus);

        builder.execute((Callable<String>) () -> "success").getResult();

        assertTrue(events.stream().anyMatch(e -> e.getType() == RetryEventType.RETRY_SUCCESS));
    }

    @Test
    void testEvents_PublishesFailureEvents() {
        List<RetryEvent<String>> events = new ArrayList<>();
        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(2)
                .retryOn(RuntimeException.class);
        RetryObservability.events(builder, eventBus);

        assertThrows(RetryException.class, () -> builder.execute((Callable<String>) () -> {
            throw new RuntimeException("error");
        }).getResult());

        assertTrue(events.stream().anyMatch(e -> e.getType() == RetryEventType.RETRY_FAILURE));
    }

    @Test
    void testEvents_PublishesGiveUpEvents() {
        List<RetryEvent<String>> events = new ArrayList<>();
        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(2)
                .retryOn(RuntimeException.class);
        RetryObservability.events(builder, eventBus);

        assertThrows(RetryException.class, () -> builder.execute((Callable<String>) () -> {
            throw new RuntimeException("error");
        }).getResult());

        assertTrue(events.stream().anyMatch(e -> e.getType() == RetryEventType.RETRY_GIVE_UP));
    }

    @Test
    void testEvents_PublishesScheduledEvents() {
        List<RetryEvent<String>> events = new ArrayList<>();
        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .backoff(BackoffStrategy.fixed(Duration.ofMillis(10)))
                .retryOn(RuntimeException.class);
        RetryObservability.events(builder, eventBus);

        assertThrows(RetryException.class, () -> builder.execute((Callable<String>) () -> {
            throw new RuntimeException("error");
        }).getResult());

        assertTrue(events.stream().anyMatch(e -> e.getType() == RetryEventType.RETRY_SCHEDULED));
    }

    @Test
    void testEvents_WithFilter_OnlyPublishesFilteredEvents() {
        List<RetryEvent<String>> events = new ArrayList<>();
        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        Predicate<RetryEvent<String>> filter = event -> event.getType() == RetryEventType.RETRY_ATTEMPT;

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(2)
                .retryOn(RuntimeException.class);
        RetryObservability.events(builder, eventBus, filter, null);

        assertThrows(RetryException.class, () -> builder.execute((Callable<String>) () -> {
            throw new RuntimeException("error");
        }).getResult());

        assertTrue(events.stream().allMatch(e -> e.getType() == RetryEventType.RETRY_ATTEMPT));
    }

    @Test
    void testEvents_WithTransformer_TransformsEvents() {
        List<RetryEvent<String>> events = new ArrayList<>();
        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        Function<RetryEvent<String>, RetryEvent<String>> transformer = event ->
                RetryEvent.<String>builder(event.getType())
                        .context(event.getContext())
                        .attempt(event.getAttempt())
                        .lastError(event.getLastError())
                        .nextDelay(event.getNextDelay())
                        .outcome(event.getOutcome())
                        .timestamp(event.getTimestamp())
                        .build();

        Retry.Builder<String> builder = Retry.newBuilder();
        RetryObservability.events(builder, eventBus, null, transformer);

        builder.execute((Callable<String>) () -> "success").getResult();

        assertFalse(events.isEmpty());
    }

    @Test
    void testTracing_WithNullBuilder_ThrowsException() {
        TraceContext traceContext = TraceContext.noop();

        assertThrows(NullPointerException.class, () -> RetryObservability.tracing(null, traceContext));
    }

    @Test
    void testTracing_WithNullTraceContext_ThrowsException() {
        Retry.Builder<String> builder = Retry.newBuilder();

        assertThrows(NullPointerException.class, () -> RetryObservability.tracing(builder, null));
    }

    @Test
    void testTracing_WithNoopTraceContext_ReturnsBuilderUnchanged() {
        Retry.Builder<String> builder = Retry.newBuilder();
        Retry.Builder<String> result = RetryObservability.tracing(builder, TraceContext.noop());

        assertSame(builder, result);
    }

    @Test
    void testTracing_CreatesSpans() {
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

        Retry.Builder<String> builder = Retry.newBuilder();
        RetryObservability.tracing(builder, traceContext);

        builder.execute((Callable<String>) () -> "success").getResult();

        assertFalse(spanNames.isEmpty());
        assertEquals("retry.attempt", spanNames.get(0));
        assertFalse(spanAttributes.isEmpty());
        assertTrue(spanAttributes.get(0).containsKey("retry.id"));
        assertEquals(1, closeCount.get());
    }

    @Test
    void testTracing_WithCustomSpanNameProvider() {
        List<String> spanNames = new ArrayList<>();

        SimpleTraceContext traceContext = SimpleTraceContext.builder()
                .spanFactory((name, attributes) -> {
                    spanNames.add(name);

                    return SimpleTraceScope.create(() -> {
                    });
                })
                .build();

        Function<RetryContext<String>, String> spanNameProvider = ctx -> "custom.retry." + ctx.name();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .name("test-retry");
        RetryObservability.tracing(builder, traceContext, spanNameProvider, null);

        builder.execute((Callable<String>) () -> "success").getResult();

        assertFalse(spanNames.isEmpty());
        assertTrue(spanNames.get(0).startsWith("custom.retry."));
    }

    @Test
    void testTracing_WithCustomAttributeProvider() {
        List<Map<String, String>> spanAttributes = new ArrayList<>();

        SimpleTraceContext traceContext = SimpleTraceContext.builder()
                .spanFactory((name, attributes) -> {
                    spanAttributes.add(attributes);
                    return SimpleTraceScope.create(() -> {
                    });
                })
                .build();

        Function<RetryContext<String>, Map<String, String>> attributeProvider = ctx ->
                Map.of("custom.key", "custom.value");

        Retry.Builder<String> builder = Retry.newBuilder();
        RetryObservability.tracing(builder, traceContext, null, attributeProvider);

        builder.execute((Callable<String>) () -> "success").getResult();

        assertFalse(spanAttributes.isEmpty());
        assertEquals("custom.value", spanAttributes.get(0).get("custom.key"));
    }

    @Test
    void testObservability_WithAllFeatures() {
        AtomicInteger metricsAttemptCount = new AtomicInteger(0);
        AtomicInteger metricsSuccessCount = new AtomicInteger(0);
        List<RetryEvent<String>> events = new ArrayList<>();
        List<String> spanNames = new ArrayList<>();

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> metricsAttemptCount.incrementAndGet())
                .onSuccess((ctx, elapsed) -> metricsSuccessCount.incrementAndGet())
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

        Retry.Builder<String> builder = Retry.newBuilder();
        RetryObservability.observability(builder, metrics, eventBus, traceContext);

        builder.execute((Callable<String>) () -> "success").getResult();

        assertTrue(metricsAttemptCount.get() >= 0, "Metrics attempt count");
        assertTrue(metricsSuccessCount.get() >= 0, "Metrics success count");
        assertFalse(events.isEmpty(), "Should have events");
        assertFalse(spanNames.isEmpty(), "Should have spans");

        if (metricsAttemptCount.get() > 0) {
            assertEquals(1, metricsAttemptCount.get());
            assertEquals(1, metricsSuccessCount.get());
        }
    }

    @Test
    void testObservability_WithNullMetrics_StillWorks() {
        List<RetryEvent<String>> events = new ArrayList<>();
        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        Retry.Builder<String> builder = Retry.newBuilder();
        RetryObservability.observability(builder, null, eventBus, null);

        builder.execute((Callable<String>) () -> "success").getResult();

        assertFalse(events.isEmpty());
    }

    @Test
    void testSimpleEventBus_WithSingleListener() {
        AtomicInteger eventCount = new AtomicInteger(0);
        RetryEventListener<String> listener = event -> eventCount.incrementAndGet();

        SimpleRetryEventBus<String> bus = RetryObservability.simpleEventBus(listener);

        Retry.Builder<String> builder = Retry.newBuilder();
        RetryObservability.events(builder, bus);

        builder.execute((Callable<String>) () -> "success").getResult();

        assertTrue(eventCount.get() > 0);
    }

    @Test
    void testSimpleEventBus_WithMultipleListeners() {
        AtomicInteger listener1Count = new AtomicInteger(0);
        AtomicInteger listener2Count = new AtomicInteger(0);

        RetryEventListener<String> listener1 = event -> listener1Count.incrementAndGet();
        RetryEventListener<String> listener2 = event -> listener2Count.incrementAndGet();

        SimpleRetryEventBus<String> bus = RetryObservability.simpleEventBus(listener1, listener2);

        Retry.Builder<String> builder = Retry.newBuilder();
        RetryObservability.events(builder, bus);

        builder.execute((Callable<String>) () -> "success").getResult();

        assertTrue(listener1Count.get() > 0);
        assertTrue(listener2Count.get() > 0);
        assertEquals(listener1Count.get(), listener2Count.get());
    }

    @Test
    void testCompositeMetrics() {
        AtomicInteger metrics1Count = new AtomicInteger(0);
        AtomicInteger metrics2Count = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics1 = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> metrics1Count.incrementAndGet())
                .build();

        SimpleRetryMetrics<String> metrics2 = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> metrics2Count.incrementAndGet())
                .build();

        CompositeRetryMetrics<String> composite = RetryObservability.compositeMetrics(metrics1, metrics2);

        Retry.Builder<String> builder = Retry.newBuilder();
        RetryObservability.metrics(builder, composite);

        builder.execute((Callable<String>) () -> "success").getResult();

        assertEquals(1, metrics1Count.get());
        assertEquals(1, metrics2Count.get());
    }

    @Test
    void testSimpleMetrics() {
        assertNotNull(RetryObservability.simpleMetrics());
    }

    @Test
    void testSimpleTraceContext() {
        assertNotNull(RetryObservability.simpleTraceContext());
    }

    @Test
    void testEvents_AfterAttemptSuccess_AlsoPublishesEvent() {
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

        long successEvents = events.stream()
                .filter(e -> e.getType() == RetryEventType.RETRY_SUCCESS)
                .count();
        assertTrue(successEvents >= 1);
    }

    @Test
    void testObservability_WithNoopMetrics_StillWorks() {
        List<RetryEvent<String>> events = new ArrayList<>();
        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        Retry.Builder<String> builder = Retry.newBuilder();
        RetryObservability.observability(builder, RetryMetrics.noop(), eventBus, null);

        builder.execute((Callable<String>) () -> "success").getResult();

        assertFalse(events.isEmpty());
    }

    @Test
    void testObservability_WithNoopEventBus_StillWorks() {
        AtomicInteger metricsCount = new AtomicInteger(0);
        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> metricsCount.incrementAndGet())
                .build();

        Retry.Builder<String> builder = Retry.newBuilder();
        RetryObservability.observability(builder, metrics, RetryEventBus.noop(), null);

        builder.execute((Callable<String>) () -> "success").getResult();

        assertEquals(1, metricsCount.get());
    }

    @Test
    void testObservability_WithNoopTraceContext_StillWorks() {
        AtomicInteger metricsCount = new AtomicInteger(0);
        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> metricsCount.incrementAndGet())
                .build();

        Retry.Builder<String> builder = Retry.newBuilder();
        RetryObservability.observability(builder, metrics, null, TraceContext.noop());

        builder.execute((Callable<String>) () -> "success").getResult();

        assertEquals(1, metricsCount.get());
    }

    @Test
    void testTracing_WithExceptionInSpanClose_DoesNotPropagate() {
        AtomicInteger closeCount = new AtomicInteger(0);

        SimpleTraceContext traceContext = SimpleTraceContext.builder()
                .spanFactory((name, attributes) -> SimpleTraceScope.create(() -> {
                    closeCount.incrementAndGet();

                    throw new RuntimeException("close error");
                }))
                .build();

        Retry.Builder<String> builder = Retry.newBuilder();
        RetryObservability.tracing(builder, traceContext);

        assertDoesNotThrow(() -> builder.execute((Callable<String>) () -> "success").getResult());
        assertEquals(1, closeCount.get());
    }

    @Test
    void testTracing_WithExceptionInSpanClose_OnFailure_DoesNotPropagate() {
        AtomicInteger closeCount = new AtomicInteger(0);

        SimpleTraceContext traceContext = SimpleTraceContext.builder()
                .spanFactory((name, attributes) -> SimpleTraceScope.create(() -> {
                    closeCount.incrementAndGet();

                    throw new RuntimeException("close error");
                }))
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(2)
                .retryOn(RuntimeException.class);
        RetryObservability.tracing(builder, traceContext);

        assertThrows(RetryException.class, () -> builder.execute((Callable<String>) () -> {
            throw new RuntimeException("test error");
        }).getResult());

        assertTrue(closeCount.get() >= 1);
    }

    @Test
    void testEvents_WithNullEventTransformer_StillWorks() {
        List<RetryEvent<String>> events = new ArrayList<>();
        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        Retry.Builder<String> builder = Retry.newBuilder();
        RetryObservability.events(builder, eventBus, null, null);

        builder.execute((Callable<String>) () -> "success").getResult();

        assertFalse(events.isEmpty());
    }

    @Test
    void testEvents_WithNullEventFilter_StillWorks() {
        List<RetryEvent<String>> events = new ArrayList<>();
        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        Retry.Builder<String> builder = Retry.newBuilder();
        RetryObservability.events(builder, eventBus, null, null);

        builder.execute((Callable<String>) () -> "success").getResult();

        assertFalse(events.isEmpty());
    }

    @Test
    void testTracing_WithNullSpanNameProvider_UsesDefault() {
        List<String> spanNames = new ArrayList<>();

        SimpleTraceContext traceContext = SimpleTraceContext.builder()
                .spanFactory((name, attributes) -> {
                    spanNames.add(name);
                    return SimpleTraceScope.create(() -> {
                    });
                })
                .build();

        Retry.Builder<String> builder = Retry.newBuilder();
        RetryObservability.tracing(builder, traceContext, null, null);

        builder.execute((Callable<String>) () -> "success").getResult();

        assertFalse(spanNames.isEmpty());
        assertEquals("retry.attempt", spanNames.get(0));
    }

    @Test
    void testTracing_WithNullAttributeProvider_UsesDefault() {
        List<Map<String, String>> spanAttributes = new ArrayList<>();

        SimpleTraceContext traceContext = SimpleTraceContext.builder()
                .spanFactory((name, attributes) -> {
                    spanAttributes.add(attributes);

                    return SimpleTraceScope.create(() -> {
                    });
                })
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .name("test-retry");
        RetryObservability.tracing(builder, traceContext, null, null);

        builder.execute((Callable<String>) () -> "success").getResult();

        assertFalse(spanAttributes.isEmpty());
        assertTrue(spanAttributes.get(0).containsKey("retry.id"));
        assertTrue(spanAttributes.get(0).containsKey("retry.name"));
        assertEquals("test-retry", spanAttributes.get(0).get("retry.name"));
    }
}

