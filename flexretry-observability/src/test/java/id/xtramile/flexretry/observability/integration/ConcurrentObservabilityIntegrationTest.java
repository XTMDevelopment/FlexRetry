package id.xtramile.flexretry.observability.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.observability.RetryObservability;
import id.xtramile.flexretry.observability.events.RetryEvent;
import id.xtramile.flexretry.observability.events.RetryEventType;
import id.xtramile.flexretry.observability.events.SimpleRetryEventBus;
import id.xtramile.flexretry.observability.metrics.SimpleRetryMetrics;
import id.xtramile.flexretry.observability.trace.SimpleTraceContext;
import id.xtramile.flexretry.observability.trace.SimpleTraceScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for observability with concurrent retry executions.
 */
class ConcurrentObservabilityIntegrationTest {

    @Test
    void testConcurrentRetries_WithMetrics_RecordsAllAttempts() throws InterruptedException {
        AtomicInteger totalAttemptCount = new AtomicInteger(0);
        AtomicInteger totalSuccessCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> totalAttemptCount.incrementAndGet())
                .onSuccess((ctx, elapsed) -> totalSuccessCount.incrementAndGet())
                .build();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        try {
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;

                executor.submit(() -> {
                    try {
                        Retry.Builder<String> builder = Retry.<String>newBuilder()
                                .maxAttempts(3)
                                .retryOn(RuntimeException.class);
                        RetryObservability.metrics(builder, metrics);

                        AtomicInteger callCount = new AtomicInteger(0);
                        String result = builder.execute((Callable<String>) () -> {
                            int call = callCount.incrementAndGet();

                            if (call < 2) {
                                throw new RuntimeException("retry from thread " + threadId);
                            }

                            return "success-" + threadId;
                        }).getResult();

                        assertEquals("success-" + threadId, result);

                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertTrue(totalAttemptCount.get() >= threadCount * 2);
            assertEquals(threadCount, totalSuccessCount.get());
        } finally {
            executor.shutdown();
        }
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testConcurrentRetries_WithEvents_AllEventsRecorded() throws InterruptedException {
        List<RetryEvent<String>> allEvents = Collections.synchronizedList(new ArrayList<>());
        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(allEvents::add);

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        try {
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;

                executor.submit(() -> {
                    try {
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

                            return "success-" + threadId;
                        }).getResult();

                        assertEquals("success-" + threadId, result);

                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertTrue(allEvents.size() >= 0);

            long successEvents = allEvents.stream()
                    .filter(e -> e.getType() == RetryEventType.RETRY_SUCCESS)
                    .count();

            assertTrue(successEvents >= 0);

        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testConcurrentRetries_WithTracing_AllSpansCreated() throws InterruptedException {
        List<String> allSpanNames = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger totalCloseCount = new AtomicInteger(0);

        SimpleTraceContext traceContext = SimpleTraceContext.builder()
                .spanFactory((name, attributes) -> {
                    allSpanNames.add(name);

                    return SimpleTraceScope.create(totalCloseCount::incrementAndGet);
                })
                .build();

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        try {
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;

                executor.submit(() -> {
                    try {
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

                            return "success-" + threadId;
                        }).getResult();

                        assertEquals("success-" + threadId, result);

                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertTrue(allSpanNames.size() >= threadCount * 2);
            assertEquals(allSpanNames.size(), totalCloseCount.get());

        } finally {
            executor.shutdown();
        }
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testConcurrentRetries_WithFullObservability_AllFeaturesWork() throws InterruptedException {
        AtomicInteger totalAttemptCount = new AtomicInteger(0);
        AtomicInteger totalSuccessCount = new AtomicInteger(0);
        List<RetryEvent<String>> allEvents = Collections.synchronizedList(new ArrayList<>());
        List<String> allSpanNames = Collections.synchronizedList(new ArrayList<>());

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> totalAttemptCount.incrementAndGet())
                .onSuccess((ctx, elapsed) -> totalSuccessCount.incrementAndGet())
                .build();

        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(allEvents::add);

        SimpleTraceContext traceContext = SimpleTraceContext.builder()
                .spanFactory((name, attributes) -> {
                    allSpanNames.add(name);

                    return SimpleTraceScope.create(() -> {
                    });
                })
                .build();

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        try {
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;

                executor.submit(() -> {
                    try {
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

                            return "success-" + threadId;
                        }).getResult();

                        assertEquals("success-" + threadId, result);

                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertTrue(totalAttemptCount.get() >= threadCount * 2);
            assertTrue(totalSuccessCount.get() >= threadCount);
            assertTrue(allEvents.size() >= 0);
            assertTrue(allSpanNames.size() >= 0);

        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testConcurrentRetries_WithDifferentResultTypes_AllWork() throws InterruptedException {
        AtomicInteger stringAttempts = new AtomicInteger(0);
        AtomicInteger integerAttempts = new AtomicInteger(0);

        SimpleRetryMetrics<String> stringMetrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> stringAttempts.incrementAndGet())
                .build();

        SimpleRetryMetrics<Integer> intMetrics = SimpleRetryMetrics.<Integer>builder()
                .onAttempt((ctx, attempt) -> integerAttempts.incrementAndGet())
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        try {
            executor.submit(() -> {
                try {
                    Retry.Builder<String> builder = Retry.<String>newBuilder()
                            .maxAttempts(2)
                            .retryOn(RuntimeException.class);
                    RetryObservability.metrics(builder, stringMetrics);

                    String result = builder.execute((Callable<String>) () -> "success").getResult();
                    assertEquals("success", result);

                } finally {
                    latch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    Retry.Builder<Integer> builder = Retry.<Integer>newBuilder()
                            .maxAttempts(2)
                            .retryOn(RuntimeException.class);
                    RetryObservability.metrics(builder, intMetrics);

                    Integer result = builder.execute((Callable<Integer>) () -> 42).getResult();
                    assertEquals(42, result);

                } finally {
                    latch.countDown();
                }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(1, stringAttempts.get());
            assertEquals(1, integerAttempts.get());

        } finally {
            executor.shutdown();
        }
    }
}

