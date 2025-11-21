package id.xtramile.flexretry.lifecycle;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.RetryContext;
import id.xtramile.flexretry.exception.RetryException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AttemptLifecycleTest {

    @Test
    void testBeforeAttempt() {
        AttemptLifecycle<String> lifecycle = new AttemptLifecycle<>() {
            @Override
            public void beforeAttempt(RetryContext<String> ctx) {
                // Default implementation does nothing
            }
        };
        
        RetryContext<String> ctx = new RetryContext<>(
                "id", "name", 1, 3, null, null, Duration.ZERO, Map.of()
        );

        assertDoesNotThrow(() -> lifecycle.beforeAttempt(ctx));
    }

    @Test
    void testAfterSuccess() {
        AttemptLifecycle<String> lifecycle = new AttemptLifecycle<>() {
            @Override
            public void afterSuccess(RetryContext<String> ctx) {
                // Default implementation does nothing
            }
        };
        
        RetryContext<String> ctx = new RetryContext<>(
                "id", "name", 1, 3, "result", null, Duration.ZERO, Map.of()
        );

        assertDoesNotThrow(() -> lifecycle.afterSuccess(ctx));
    }

    @Test
    void testAfterFailure() {
        AttemptLifecycle<String> lifecycle = new AttemptLifecycle<>() {
            @Override
            public void afterFailure(RetryContext<String> ctx, Throwable error) {
                // Default implementation does nothing
            }
        };
        
        RetryContext<String> ctx = new RetryContext<>(
                "id", "name", 1, 3, null, new RuntimeException("error"), Duration.ZERO, Map.of()
        );

        assertDoesNotThrow(() -> lifecycle.afterFailure(ctx, new RuntimeException("error")));
    }

    @Test
    void testCustomLifecycle() {
        AttemptLifecycle<String> lifecycle = new AttemptLifecycle<>() {
            @Override
            public void beforeAttempt(RetryContext<String> ctx) {
                // Custom implementation
            }

            @Override
            public void afterSuccess(RetryContext<String> ctx) {
                // Custom implementation
            }

            @Override
            public void afterFailure(RetryContext<String> ctx, Throwable error) {
                // Custom implementation
            }
        };
        
        RetryContext<String> ctx = new RetryContext<>(
                "id", "name", 1, 3, null, null, Duration.ZERO, Map.of()
        );
        
        assertDoesNotThrow(() -> lifecycle.beforeAttempt(ctx));
        assertDoesNotThrow(() -> lifecycle.afterSuccess(ctx));
        assertDoesNotThrow(() -> lifecycle.afterFailure(ctx, new RuntimeException("error")));
    }

    @Test
    void testCustomBeforeAttemptWithStateTracking() {
        AtomicInteger beforeAttemptCount = new AtomicInteger(0);
        List<Integer> attemptNumbers = new ArrayList<>();
        AtomicReference<String> capturedName = new AtomicReference<>();

        AttemptLifecycle<String> lifecycle = new AttemptLifecycle<>() {
            @Override
            public void beforeAttempt(RetryContext<String> ctx) {
                beforeAttemptCount.incrementAndGet();
                attemptNumbers.add(ctx.attempt());
                capturedName.set(ctx.name());
            }
        };

        RetryContext<String> ctx1 = new RetryContext<>(
                "id1", "test-retry", 1, 3, null, null, Duration.ZERO, Map.of()
        );
        RetryContext<String> ctx2 = new RetryContext<>(
                "id1", "test-retry", 2, 3, null, null, Duration.ZERO, Map.of()
        );

        lifecycle.beforeAttempt(ctx1);
        lifecycle.beforeAttempt(ctx2);

        assertEquals(2, beforeAttemptCount.get());
        assertEquals(2, attemptNumbers.size());
        assertEquals(1, attemptNumbers.get(0));
        assertEquals(2, attemptNumbers.get(1));
        assertEquals("test-retry", capturedName.get());
    }

    @Test
    void testCustomAfterSuccessWithResultTracking() {
        AtomicInteger afterSuccessCount = new AtomicInteger(0);
        List<String> capturedResults = new ArrayList<>();
        AtomicReference<Integer> finalAttempt = new AtomicReference<>();

        AttemptLifecycle<String> lifecycle = new AttemptLifecycle<>() {
            @Override
            public void afterSuccess(RetryContext<String> ctx) {
                afterSuccessCount.incrementAndGet();
                capturedResults.add(ctx.lastResult());
                finalAttempt.set(ctx.attempt());
            }
        };

        RetryContext<String> ctx = new RetryContext<>(
                "id", "name", 2, 3, "success-result", null, Duration.ZERO, Map.of()
        );

        lifecycle.afterSuccess(ctx);

        assertEquals(1, afterSuccessCount.get());
        assertEquals(1, capturedResults.size());
        assertEquals("success-result", capturedResults.get(0));
        assertEquals(2, finalAttempt.get());
    }

    @Test
    void testCustomAfterFailureWithErrorTracking() {
        AtomicInteger afterFailureCount = new AtomicInteger(0);
        List<Throwable> capturedErrors = new ArrayList<>();
        List<Integer> attemptNumbers = new ArrayList<>();

        AttemptLifecycle<String> lifecycle = new AttemptLifecycle<>() {
            @Override
            public void afterFailure(RetryContext<String> ctx, Throwable error) {
                afterFailureCount.incrementAndGet();
                capturedErrors.add(error);
                attemptNumbers.add(ctx.attempt());
            }
        };

        RuntimeException error1 = new RuntimeException("error1");
        IllegalArgumentException error2 = new IllegalArgumentException("error2");

        RetryContext<String> ctx1 = new RetryContext<>(
                "id", "name", 1, 3, null, error1, Duration.ZERO, Map.of()
        );
        RetryContext<String> ctx2 = new RetryContext<>(
                "id", "name", 2, 3, null, error2, Duration.ZERO, Map.of()
        );

        lifecycle.afterFailure(ctx1, error1);
        lifecycle.afterFailure(ctx2, error2);

        assertEquals(2, afterFailureCount.get());
        assertEquals(2, capturedErrors.size());
        assertSame(error1, capturedErrors.get(0));
        assertSame(error2, capturedErrors.get(1));
        assertEquals(2, attemptNumbers.size());
        assertEquals(1, attemptNumbers.get(0));
        assertEquals(2, attemptNumbers.get(1));
    }

    @Test
    void testCustomLifecycleWithRetryExecution() {
        AtomicInteger beforeAttemptCount = new AtomicInteger(0);
        AtomicInteger afterSuccessCount = new AtomicInteger(0);
        AtomicInteger afterFailureCount = new AtomicInteger(0);
        List<Integer> attemptSequence = new ArrayList<>();

        AttemptLifecycle<String> lifecycle = new AttemptLifecycle<>() {
            @Override
            public void beforeAttempt(RetryContext<String> ctx) {
                beforeAttemptCount.incrementAndGet();
                attemptSequence.add(ctx.attempt());
            }

            @Override
            public void afterSuccess(RetryContext<String> ctx) {
                afterSuccessCount.incrementAndGet();
            }

            @Override
            public void afterFailure(RetryContext<String> ctx, Throwable error) {
                afterFailureCount.incrementAndGet();
            }
        };

        AtomicInteger executionCount = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryOn(RuntimeException.class)
                .lifecycle(lifecycle)
                .execute((Callable<String>) () -> {
                    int count = executionCount.incrementAndGet();
                    if (count < 2) {
                        throw new RuntimeException("retry");
                    }
                    return "success";
                })
                .getResult();

        assertEquals("success", result);
        assertEquals(2, beforeAttemptCount.get());
        assertEquals(1, afterSuccessCount.get());
        assertEquals(1, afterFailureCount.get());
        assertEquals(2, attemptSequence.size());
        assertEquals(1, attemptSequence.get(0));
        assertEquals(2, attemptSequence.get(1));
    }

    @Test
    void testCustomLifecycleWithMultipleRetries() {
        AtomicInteger beforeAttemptCount = new AtomicInteger(0);
        AtomicInteger afterFailureCount = new AtomicInteger(0);
        List<String> errorMessages = new ArrayList<>();

        AttemptLifecycle<String> lifecycle = new AttemptLifecycle<>() {
            @Override
            public void beforeAttempt(RetryContext<String> ctx) {
                beforeAttemptCount.incrementAndGet();
            }

            @Override
            public void afterFailure(RetryContext<String> ctx, Throwable error) {
                afterFailureCount.incrementAndGet();
                errorMessages.add(error.getMessage());
            }
        };

        assertThrows(RetryException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(3)
                    .retryOn(RuntimeException.class)
                    .lifecycle(lifecycle)
                    .execute((Callable<String>) () -> {
                        throw new RuntimeException("attempt " + beforeAttemptCount.get());
                    })
                    .getResult());

        assertEquals(3, beforeAttemptCount.get());
        assertEquals(3, afterFailureCount.get());
        assertEquals(3, errorMessages.size());
        assertTrue(errorMessages.get(0).contains("attempt"));
    }

    @Test
    void testCustomLifecycleWithContextInformation() {
        AtomicReference<String> capturedId = new AtomicReference<>();
        AtomicReference<Map<String, Object>> capturedTags = new AtomicReference<>();
        AtomicReference<Duration> capturedDelay = new AtomicReference<>();

        AttemptLifecycle<String> lifecycle = new AttemptLifecycle<>() {
            @Override
            public void beforeAttempt(RetryContext<String> ctx) {
                capturedId.set(ctx.id());
                capturedTags.set(ctx.tags());
                capturedDelay.set(ctx.nextDelay());
            }
        };

        Retry.<String>newBuilder()
                .id("custom-id")
                .tag("env", "test")
                .tag("version", "1.0")
                .maxAttempts(1)
                .delayMillis(100)
                .lifecycle(lifecycle)
                .execute((Callable<String>) () -> "success")
                .getResult();

        assertEquals("custom-id", capturedId.get());
        assertNotNull(capturedTags.get());
        assertEquals("test", capturedTags.get().get("env"));
        assertEquals("1.0", capturedTags.get().get("version"));
        assertNotNull(capturedDelay.get());
    }

    @Test
    void testCustomLifecycleOnlyBeforeAttempt() {
        AtomicInteger beforeAttemptCount = new AtomicInteger(0);

        AttemptLifecycle<String> lifecycle = new AttemptLifecycle<>() {
            @Override
            public void beforeAttempt(RetryContext<String> ctx) {
                beforeAttemptCount.incrementAndGet();
            }
            // afterSuccess and afterFailure use default noop implementations
        };

        Retry.<String>newBuilder()
                .maxAttempts(2)
                .lifecycle(lifecycle)
                .execute((Callable<String>) () -> "success")
                .getResult();

        assertEquals(1, beforeAttemptCount.get());
    }

    @Test
    void testCustomLifecycleOnlyAfterSuccess() {
        AtomicInteger afterSuccessCount = new AtomicInteger(0);

        AttemptLifecycle<String> lifecycle = new AttemptLifecycle<>() {
            @Override
            public void afterSuccess(RetryContext<String> ctx) {
                afterSuccessCount.incrementAndGet();
            }
            // beforeAttempt and afterFailure use default noop implementations
        };

        Retry.<String>newBuilder()
                .maxAttempts(2)
                .lifecycle(lifecycle)
                .execute((Callable<String>) () -> "success")
                .getResult();

        assertEquals(1, afterSuccessCount.get());
    }

    @Test
    void testCustomLifecycleOnlyAfterFailure() {
        AtomicInteger afterFailureCount = new AtomicInteger(0);

        AttemptLifecycle<String> lifecycle = new AttemptLifecycle<>() {
            @Override
            public void afterFailure(RetryContext<String> ctx, Throwable error) {
                afterFailureCount.incrementAndGet();
            }
            // beforeAttempt and afterSuccess use default noop implementations
        };

        assertThrows(RetryException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(2)
                    .retryOn(RuntimeException.class)
                    .lifecycle(lifecycle)
                    .execute((Callable<String>) () -> {
                        throw new RuntimeException("error");
                    })
                    .getResult());

        assertEquals(2, afterFailureCount.get());
    }
}

