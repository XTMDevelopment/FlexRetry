package id.xtramile.flexretry.observability.metrics;

import id.xtramile.flexretry.RetryContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class SimpleRetryMetricsTest {

    @Test
    void testBuilder() {
        SimpleRetryMetrics.Builder<String> builder = SimpleRetryMetrics.builder();

        assertNotNull(builder);
    }

    @Test
    void testOnScheduled() {
        AtomicInteger callCount = new AtomicInteger(0);

        Consumer<RetryContext<String>> handler = ctx -> callCount.incrementAndGet();

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onScheduled(handler)
                .build();

        RetryContext<String> context = createMockContext();
        metrics.onScheduled(context, 1, Duration.ofMillis(100));

        assertEquals(1, callCount.get());
    }

    @Test
    void testOnScheduled_WithNullHandler() {
        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .build();

        RetryContext<String> context = createMockContext();
        assertDoesNotThrow(() -> metrics.onScheduled(context, 1, Duration.ofMillis(100)));
    }

    @Test
    void testOnAttempt() {
        AtomicInteger callCount = new AtomicInteger(0);
        AtomicInteger capturedAttempt = new AtomicInteger(0);

        BiConsumer<RetryContext<String>, Integer> handler = (ctx, attempt) -> {
            callCount.incrementAndGet();
            capturedAttempt.set(attempt);
        };

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt(handler)
                .build();

        RetryContext<String> context = createMockContext();
        metrics.onAttempt(context, 3);

        assertEquals(1, callCount.get());
        assertEquals(3, capturedAttempt.get());
    }

    @Test
    void testOnAttempt_WithNullHandler() {
        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .build();

        RetryContext<String> context = createMockContext();
        assertDoesNotThrow(() -> metrics.onAttempt(context, 1));
    }

    @Test
    void testOnSuccess() {
        AtomicInteger callCount = new AtomicInteger(0);

        BiConsumer<RetryContext<String>, Duration> handler =
                (ctx, elapsed) -> callCount.incrementAndGet();

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onSuccess(handler)
                .build();

        RetryContext<String> context = createMockContext();
        Duration elapsed = Duration.ofMillis(150);
        metrics.onSuccess(context, 1, elapsed);

        assertEquals(1, callCount.get());
    }

    @Test
    void testOnSuccess_WithNullHandler() {
        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .build();

        RetryContext<String> context = createMockContext();
        assertDoesNotThrow(() -> metrics.onSuccess(context, 1, Duration.ofMillis(100)));
    }

    @Test
    void testOnFailure() {
        AtomicInteger callCount = new AtomicInteger(0);

        SimpleRetryMetrics.TriConsumer<RetryContext<String>, Throwable, Duration> handler =
                (ctx, error, elapsed) -> callCount.incrementAndGet();

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onFailure(handler)
                .build();

        RetryContext<String> context = createMockContext();
        RuntimeException error = new RuntimeException("test error");
        Duration elapsed = Duration.ofMillis(200);
        metrics.onFailure(context, 2, error, elapsed);

        assertEquals(1, callCount.get());
    }

    @Test
    void testOnFailure_WithNullHandler() {
        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .build();

        RetryContext<String> context = createMockContext();
        RuntimeException error = new RuntimeException("test error");
        assertDoesNotThrow(() -> metrics.onFailure(context, 1, error, Duration.ofMillis(100)));
    }

    @Test
    void testOnGiveUp() {
        AtomicInteger callCount = new AtomicInteger(0);

        SimpleRetryMetrics.TriConsumer<RetryContext<String>, Throwable, Duration> handler =
                (ctx, error, elapsed) -> callCount.incrementAndGet();

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onGiveUp(handler)
                .build();

        RetryContext<String> context = createMockContext();
        RuntimeException error = new RuntimeException("test error");
        Duration elapsed = Duration.ofMillis(300);
        metrics.onGiveUp(context, 3, error, elapsed);

        assertEquals(1, callCount.get());
    }

    @Test
    void testOnGiveUp_WithNullHandler() {
        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .build();

        RetryContext<String> context = createMockContext();
        RuntimeException error = new RuntimeException("test error");

        assertDoesNotThrow(() -> metrics.onGiveUp(context, 1, error, Duration.ofMillis(100)));
    }

    @Test
    void testBuild_WithAllHandlers() {
        AtomicInteger scheduledCount = new AtomicInteger(0);
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger giveUpCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onScheduled(ctx -> scheduledCount.incrementAndGet())
                .onAttempt((ctx, attempt) -> attemptCount.incrementAndGet())
                .onSuccess((ctx, elapsed) -> successCount.incrementAndGet())
                .onFailure((ctx, error, elapsed) -> failureCount.incrementAndGet())
                .onGiveUp((ctx, error, elapsed) -> giveUpCount.incrementAndGet())
                .build();

        RetryContext<String> context = createMockContext();
        metrics.onScheduled(context, 1, Duration.ofMillis(100));
        metrics.onAttempt(context, 1);
        metrics.onSuccess(context, 1, Duration.ofMillis(150));
        metrics.onFailure(context, 2, new RuntimeException("error"), Duration.ofMillis(200));
        metrics.onGiveUp(context, 3, new RuntimeException("error"), Duration.ofMillis(300));

        assertEquals(1, scheduledCount.get());
        assertEquals(1, attemptCount.get());
        assertEquals(1, successCount.get());
        assertEquals(1, failureCount.get());
        assertEquals(1, giveUpCount.get());
    }

    private RetryContext<String> createMockContext() {
        return new RetryContext<>(
                "test-id", "test-name", 1, 3, "test", null, Duration.ZERO, null);
    }
}

