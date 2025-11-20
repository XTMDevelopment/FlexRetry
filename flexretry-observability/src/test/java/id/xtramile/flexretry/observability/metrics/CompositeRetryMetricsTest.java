package id.xtramile.flexretry.observability.metrics;

import id.xtramile.flexretry.RetryContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CompositeRetryMetricsTest {

    @Test
    void testOf_WithNoMetrics() {
        CompositeRetryMetrics<String> composite = CompositeRetryMetrics.of();

        assertNotNull(composite);
        assertTrue(composite.getDelegates().isEmpty());
    }

    @Test
    void testOf_WithSingleMetric() {
        AtomicInteger callCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metric = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> callCount.incrementAndGet())
                .build();

        CompositeRetryMetrics<String> composite = CompositeRetryMetrics.of(metric);
        assertNotNull(composite);
        assertEquals(1, composite.getDelegates().size());

        RetryContext<String> context = createMockContext();
        composite.onAttempt(context, 1);

        assertEquals(1, callCount.get());
    }

    @Test
    void testOf_WithMultipleMetrics() {
        AtomicInteger metric1Count = new AtomicInteger(0);
        AtomicInteger metric2Count = new AtomicInteger(0);
        AtomicInteger metric3Count = new AtomicInteger(0);

        SimpleRetryMetrics<String> metric1 = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> metric1Count.incrementAndGet())
                .build();

        SimpleRetryMetrics<String> metric2 = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> metric2Count.incrementAndGet())
                .build();

        SimpleRetryMetrics<String> metric3 = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> metric3Count.incrementAndGet())
                .build();

        CompositeRetryMetrics<String> composite = CompositeRetryMetrics.of(metric1, metric2, metric3);
        assertEquals(3, composite.getDelegates().size());

        RetryContext<String> context = createMockContext();
        composite.onAttempt(context, 1);

        assertEquals(1, metric1Count.get());
        assertEquals(1, metric2Count.get());
        assertEquals(1, metric3Count.get());
    }

    @Test
    void testOnScheduled_DelegatesToAll() {
        AtomicInteger metric1Count = new AtomicInteger(0);
        AtomicInteger metric2Count = new AtomicInteger(0);

        SimpleRetryMetrics<String> metric1 = SimpleRetryMetrics.<String>builder()
                .onScheduled(ctx -> metric1Count.incrementAndGet())
                .build();

        SimpleRetryMetrics<String> metric2 = SimpleRetryMetrics.<String>builder()
                .onScheduled(ctx -> metric2Count.incrementAndGet())
                .build();

        CompositeRetryMetrics<String> composite = CompositeRetryMetrics.of(metric1, metric2);

        RetryContext<String> context = createMockContext();
        composite.onScheduled(context, 1, Duration.ofMillis(100));

        assertEquals(1, metric1Count.get());
        assertEquals(1, metric2Count.get());
    }

    @Test
    void testOnAttempt_DelegatesToAll() {
        AtomicInteger metric1Count = new AtomicInteger(0);
        AtomicInteger metric2Count = new AtomicInteger(0);

        SimpleRetryMetrics<String> metric1 = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> metric1Count.incrementAndGet())
                .build();

        SimpleRetryMetrics<String> metric2 = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> metric2Count.incrementAndGet())
                .build();

        CompositeRetryMetrics<String> composite = CompositeRetryMetrics.of(metric1, metric2);

        RetryContext<String> context = createMockContext();
        composite.onAttempt(context, 2);

        assertEquals(1, metric1Count.get());
        assertEquals(1, metric2Count.get());
    }

    @Test
    void testOnSuccess_DelegatesToAll() {
        AtomicInteger metric1Count = new AtomicInteger(0);
        AtomicInteger metric2Count = new AtomicInteger(0);

        SimpleRetryMetrics<String> metric1 = SimpleRetryMetrics.<String>builder()
                .onSuccess((ctx, elapsed) -> metric1Count.incrementAndGet())
                .build();

        SimpleRetryMetrics<String> metric2 = SimpleRetryMetrics.<String>builder()
                .onSuccess((ctx, elapsed) -> metric2Count.incrementAndGet())
                .build();

        CompositeRetryMetrics<String> composite = CompositeRetryMetrics.of(metric1, metric2);

        RetryContext<String> context = createMockContext();
        composite.onSuccess(context, 1, Duration.ofMillis(150));

        assertEquals(1, metric1Count.get());
        assertEquals(1, metric2Count.get());
    }

    @Test
    void testOnFailure_DelegatesToAll() {
        AtomicInteger metric1Count = new AtomicInteger(0);
        AtomicInteger metric2Count = new AtomicInteger(0);

        SimpleRetryMetrics<String> metric1 = SimpleRetryMetrics.<String>builder()
                .onFailure((ctx, error, elapsed) -> metric1Count.incrementAndGet())
                .build();

        SimpleRetryMetrics<String> metric2 = SimpleRetryMetrics.<String>builder()
                .onFailure((ctx, error, elapsed) -> metric2Count.incrementAndGet())
                .build();

        CompositeRetryMetrics<String> composite = CompositeRetryMetrics.of(metric1, metric2);

        RetryContext<String> context = createMockContext();
        RuntimeException error = new RuntimeException("test error");
        composite.onFailure(context, 2, error, Duration.ofMillis(200));

        assertEquals(1, metric1Count.get());
        assertEquals(1, metric2Count.get());
    }

    @Test
    void testOnGiveUp_DelegatesToAll() {
        AtomicInteger metric1Count = new AtomicInteger(0);
        AtomicInteger metric2Count = new AtomicInteger(0);

        SimpleRetryMetrics<String> metric1 = SimpleRetryMetrics.<String>builder()
                .onGiveUp((ctx, error, elapsed) -> metric1Count.incrementAndGet())
                .build();

        SimpleRetryMetrics<String> metric2 = SimpleRetryMetrics.<String>builder()
                .onGiveUp((ctx, error, elapsed) -> metric2Count.incrementAndGet())
                .build();

        CompositeRetryMetrics<String> composite = CompositeRetryMetrics.of(metric1, metric2);

        RetryContext<String> context = createMockContext();
        RuntimeException error = new RuntimeException("test error");
        composite.onGiveUp(context, 3, error, Duration.ofMillis(300));

        assertEquals(1, metric1Count.get());
        assertEquals(1, metric2Count.get());
    }

    @Test
    void testGetDelegates_ReturnsImmutableList() {
        SimpleRetryMetrics<String> metric1 = SimpleRetryMetrics.<String>builder().build();
        SimpleRetryMetrics<String> metric2 = SimpleRetryMetrics.<String>builder().build();

        CompositeRetryMetrics<String> composite = CompositeRetryMetrics.of(metric1, metric2);
        List<RetryMetrics<String>> delegates = composite.getDelegates();

        assertEquals(2, delegates.size());
        assertThrows(UnsupportedOperationException.class, () -> delegates.add(metric1));
    }

    @Test
    void testConstructor_WithNullDelegates_ThrowsException() {
        assertThrows(NullPointerException.class, () -> new CompositeRetryMetrics<>(null));
    }

    private RetryContext<String> createMockContext() {
        return new RetryContext<>(
                "test-id", "test-name", 1, 3, "test", null, Duration.ZERO, null);
    }
}

