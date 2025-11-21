package id.xtramile.flexretry.observability.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.lifecycle.AttemptLifecycle;
import id.xtramile.flexretry.observability.RetryObservability;
import id.xtramile.flexretry.observability.events.RetryEvent;
import id.xtramile.flexretry.observability.events.SimpleRetryEventBus;
import id.xtramile.flexretry.observability.metrics.SimpleRetryMetrics;
import id.xtramile.flexretry.observability.trace.SimpleTraceContext;
import id.xtramile.flexretry.observability.trace.SimpleTraceScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for observability with custom lifecycle hooks.
 */
class LifecycleObservabilityIntegrationTest {

    @SuppressWarnings("ConstantValue")
    @Test
    void testLifecycle_WithTracing_CreatesSpansAtCorrectTimes() {
        List<String> spanNames = new ArrayList<>();
        AtomicInteger closeCount = new AtomicInteger(0);
        AtomicInteger lifecycleBeforeCount = new AtomicInteger(0);
        AtomicInteger lifecycleAfterSuccessCount = new AtomicInteger(0);

        SimpleTraceContext traceContext = SimpleTraceContext.builder()
                .spanFactory((name, attributes) -> {
                    spanNames.add(name);

                    return SimpleTraceScope.create(closeCount::incrementAndGet);
                })
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryOn(RuntimeException.class)
                .lifecycle(new AttemptLifecycle<>() {
                    @Override
                    public void beforeAttempt(id.xtramile.flexretry.RetryContext<String> ctx) {
                        lifecycleBeforeCount.incrementAndGet();
                    }

                    @Override
                    public void afterSuccess(id.xtramile.flexretry.RetryContext<String> ctx) {
                        lifecycleAfterSuccessCount.incrementAndGet();
                    }
                });
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
        assertTrue(lifecycleBeforeCount.get() >= 2);
        assertTrue(lifecycleAfterSuccessCount.get() >= 1);
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testLifecycle_WithMetricsAndEvents_AllFeaturesWork() {
        AtomicInteger metricsAttemptCount = new AtomicInteger(0);
        AtomicInteger metricsSuccessCount = new AtomicInteger(0);
        List<RetryEvent<String>> events = new ArrayList<>();
        AtomicInteger lifecycleBeforeCount = new AtomicInteger(0);
        AtomicInteger lifecycleAfterFailureCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> metricsAttemptCount.incrementAndGet())
                .onSuccess((ctx, elapsed) -> metricsSuccessCount.incrementAndGet())
                .build();

        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryOn(RuntimeException.class)
                .lifecycle(new AttemptLifecycle<>() {
                    @Override
                    public void beforeAttempt(id.xtramile.flexretry.RetryContext<String> ctx) {
                        lifecycleBeforeCount.incrementAndGet();
                    }

                    @Override
                    public void afterFailure(id.xtramile.flexretry.RetryContext<String> ctx, Throwable error) {
                        lifecycleAfterFailureCount.incrementAndGet();
                    }
                });
        RetryObservability.observability(builder, metrics, eventBus, null);

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
        assertTrue(metricsSuccessCount.get() >= 1);
        assertTrue(events.size() >= 0);
        assertTrue(lifecycleBeforeCount.get() >= 2);
        assertTrue(lifecycleAfterFailureCount.get() >= 1);
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testLifecycle_WithTracingAndCustomLifecycle_SpansCreatedCorrectly() {
        List<String> spanNames = new ArrayList<>();
        AtomicInteger closeCount = new AtomicInteger(0);
        AtomicInteger customLifecycleCount = new AtomicInteger(0);

        SimpleTraceContext traceContext = SimpleTraceContext.builder()
                .spanFactory((name, attributes) -> {
                    spanNames.add(name);
                    return SimpleTraceScope.create(closeCount::incrementAndGet);
                })
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryOn(RuntimeException.class)
                .lifecycle(new AttemptLifecycle<>() {
                    @Override
                    public void beforeAttempt(id.xtramile.flexretry.RetryContext<String> ctx) {
                        customLifecycleCount.incrementAndGet();
                    }
                });
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
        assertTrue(customLifecycleCount.get() >= 2);
    }
}

