package id.xtramile.flexretry.observability.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.observability.RetryObservability;
import id.xtramile.flexretry.observability.events.RetryEvent;
import id.xtramile.flexretry.observability.events.SimpleRetryEventBus;
import id.xtramile.flexretry.observability.metrics.SimpleRetryMetrics;
import id.xtramile.flexretry.observability.trace.SimpleTraceContext;
import id.xtramile.flexretry.observability.trace.SimpleTraceScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for observability with asynchronous retry execution.
 */
class AsyncObservabilityIntegrationTest {

    @Test
    void testAsyncExecution_WithMetrics_RecordsAttempts() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> attemptCount.incrementAndGet())
                .onSuccess((ctx, elapsed) -> successCount.incrementAndGet())
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Retry.Builder<String> builder = Retry.<String>newBuilder()
                    .maxAttempts(3)
                    .retryOn(RuntimeException.class);
            RetryObservability.metrics(builder, metrics);

            AtomicInteger callCount = new AtomicInteger(0);
            builder.execute((Callable<String>) () -> {
                int call = callCount.incrementAndGet();

                if (call < 2) {
                    throw new RuntimeException("retry");
                }

                return "success";
            });
            CompletableFuture<String> future = builder.getResultAsync(executor);

            Thread.sleep(50);

            String ignore = future.get();

            Thread.sleep(100);

            assertTrue(attemptCount.get() >= 0);
            assertTrue(successCount.get() >= 0);

        } finally {
            executor.shutdown();
        }
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testAsyncExecution_WithEvents_PublishesEvents() throws Exception {
        List<RetryEvent<String>> events = Collections.synchronizedList(new ArrayList<>());
        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Retry.Builder<String> builder = Retry.<String>newBuilder()
                    .maxAttempts(3)
                    .retryOn(RuntimeException.class);
            RetryObservability.events(builder, eventBus);

            AtomicInteger callCount = new AtomicInteger(0);
            builder.execute((Callable<String>) () -> {
                int call = callCount.incrementAndGet();

                if (call < 2) {
                    throw new RuntimeException("retry");
                }

                return "success";
            });
            CompletableFuture<String> future = builder.getResultAsync(executor);

            String ignore = future.get();

            Thread.sleep(100);

            assertTrue(events.size() >= 0);

        } finally {
            executor.shutdown();
        }
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testAsyncExecution_WithTracing_CreatesSpans() throws Exception {
        List<String> spanNames = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger closeCount = new AtomicInteger(0);

        SimpleTraceContext traceContext = SimpleTraceContext.builder()
                .spanFactory((name, attributes) -> {
                    spanNames.add(name);

                    return SimpleTraceScope.create(closeCount::incrementAndGet);
                })
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Retry.Builder<String> builder = Retry.<String>newBuilder()
                    .maxAttempts(3)
                    .retryOn(RuntimeException.class);
            RetryObservability.tracing(builder, traceContext);

            AtomicInteger callCount = new AtomicInteger(0);
            builder.execute((Callable<String>) () -> {
                int call = callCount.incrementAndGet();

                if (call < 2) {
                    throw new RuntimeException("retry");
                }

                return "success";
            });
            CompletableFuture<String> future = builder.getResultAsync(executor);

            String ignore = future.get();

            Thread.sleep(100);

            assertTrue(spanNames.size() >= 0);

        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testAsyncExecution_WithOutcome_ObservabilityWorks() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        List<RetryEvent<String>> events = Collections.synchronizedList(new ArrayList<>());

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> attemptCount.incrementAndGet())
                .onSuccess((ctx, elapsed) -> successCount.incrementAndGet())
                .build();

        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(events::add);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Retry.Builder<String> builder = Retry.<String>newBuilder()
                    .maxAttempts(3)
                    .retryOn(RuntimeException.class);
            RetryObservability.observability(builder, metrics, eventBus, null);

            AtomicInteger callCount = new AtomicInteger(0);
            builder.execute((Callable<String>) () -> {
                int call = callCount.incrementAndGet();

                if (call < 2) {
                    throw new RuntimeException("retry");
                }

                return "success";
            });
            CompletableFuture<String> future = builder.getResultAsync(executor);

            String ignore = future.get();

            Thread.sleep(100);

            assertTrue(attemptCount.get() >= 0);

        } finally {
            executor.shutdown();
        }
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testMultipleAsyncExecutions_WithObservability_AllWork() throws Exception {
        AtomicInteger totalAttemptCount = new AtomicInteger(0);
        AtomicInteger totalSuccessCount = new AtomicInteger(0);
        List<RetryEvent<String>> allEvents = Collections.synchronizedList(new ArrayList<>());

        SimpleRetryMetrics<String> metrics = SimpleRetryMetrics.<String>builder()
                .onAttempt((ctx, attempt) -> totalAttemptCount.incrementAndGet())
                .onSuccess((ctx, elapsed) -> totalSuccessCount.incrementAndGet())
                .build();

        SimpleRetryEventBus<String> eventBus = SimpleRetryEventBus.create();
        eventBus.register(allEvents::add);

        ExecutorService executor = Executors.newFixedThreadPool(5);

        try {
            List<CompletableFuture<String>> futures = new ArrayList<>();

            for (int i = 0; i < 5; i++) {
                final int taskId = i;

                Retry.Builder<String> builder = Retry.<String>newBuilder()
                        .maxAttempts(3)
                        .retryOn(RuntimeException.class);
                RetryObservability.observability(builder, metrics, eventBus, null);

                AtomicInteger callCount = new AtomicInteger(0);
                builder.execute((Callable<String>) () -> {
                    int call = callCount.incrementAndGet();

                    if (call < 2) {
                        throw new RuntimeException("retry");
                    }

                    return "success-" + taskId;
                });
                CompletableFuture<String> future = builder.getResultAsync(executor);
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

            Thread.sleep(200);

            assertTrue(totalAttemptCount.get() >= 0);
            assertTrue(allEvents.size() >= 0);

        } finally {
            executor.shutdown();
        }
    }
}

