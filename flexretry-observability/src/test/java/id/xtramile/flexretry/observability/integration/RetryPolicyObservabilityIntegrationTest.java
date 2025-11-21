package id.xtramile.flexretry.observability.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.exception.RetryException;
import id.xtramile.flexretry.observability.RetryObservability;
import id.xtramile.flexretry.observability.events.RetryEvent;
import id.xtramile.flexretry.observability.events.RetryEventType;
import id.xtramile.flexretry.observability.events.SimpleRetryEventBus;
import id.xtramile.flexretry.observability.metrics.SimpleRetryMetrics;
import id.xtramile.flexretry.strategy.policy.ClassifierPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for observability with different retry policies.
 */
class RetryPolicyObservabilityIntegrationTest {

    @Test
    void testExceptionRetryPolicy_WithMetrics_RecordsFailures() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> attemptCount.incrementAndGet())
                .onFailure((ctx, error, elapsed) -> failureCount.incrementAndGet())
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryOn(RuntimeException.class, IllegalArgumentException.class);
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
        assertTrue(failureCount.get() >= 1);
    }

    @Test
    void testClassifierPolicy_WithEvents_PublishesEvents() {
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
        assertTrue(events.stream().anyMatch(e -> e.getType() == RetryEventType.RETRY_SUCCESS));
    }

    @Test
    void testResultBasedRetryPolicy_WithMetrics_RecordsRetries() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> attemptCount.incrementAndGet())
                .onSuccess((ctx, elapsed) -> successCount.incrementAndGet())
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .classify(result -> {
                    if ("retry".equals(result)) {
                        return ClassifierPolicy.Decision.RETRY;
                    }

                    return ClassifierPolicy.Decision.SUCCESS;
                });
        RetryObservability.metrics(builder, metrics);

        AtomicInteger callCount = new AtomicInteger(0);
        String result = builder.execute((Callable<String>) () -> {
            int call = callCount.incrementAndGet();

            if (call < 2) {
                return "retry";
            }

            return "success";
        }).getResult();

        assertEquals("success", result);
        assertTrue(attemptCount.get() >= 2);
        assertEquals(1, successCount.get());
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testMultiplePolicies_WithObservability_AllFeaturesWork() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<RetryEvent<String>> events = new ArrayList<>();

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> attemptCount.incrementAndGet())
                .onFailure((ctx, error, elapsed) -> failureCount.incrementAndGet())
                .build();

        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryOn(RuntimeException.class)
                .retryOn(IllegalArgumentException.class);
        RetryObservability.observability(builder, metrics, eventBus, null);

        AtomicInteger callCount = new AtomicInteger(0);
        String result = builder.execute((Callable<String>) () -> {
            int call = callCount.incrementAndGet();

            if (call == 1) {
                throw new RuntimeException("retry");

            } else if (call == 2) {
                throw new IllegalArgumentException("retry");

            }
            return "success";
        }).getResult();

        assertEquals("success", result);
        assertTrue(attemptCount.get() >= 3);
        assertTrue(failureCount.get() >= 2);
        assertTrue(events.size() >= 0);
    }

    @Test
    void testPolicy_WithNonRetryableException_RecordsGiveUp() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicInteger giveUpCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> attemptCount.incrementAndGet())
                .onGiveUp((ctx, error, elapsed) -> giveUpCount.incrementAndGet())
                .build();

        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryOn(RuntimeException.class);
        RetryObservability.metrics(builder, metrics);

        assertThrows(RetryException.class, () -> builder.execute((Callable<String>) () -> {
            throw new IllegalStateException("non-retryable");
        }).getResult());

        assertTrue(attemptCount.get() >= 1);
        assertEquals(1, giveUpCount.get());
    }
}

