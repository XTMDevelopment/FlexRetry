package id.xtramile.flexretry.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.RetryContext;
import id.xtramile.flexretry.lifecycle.AttemptLifecycle;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for lifecycle hooks
 */
class RetryLifecycleIntegrationTest {

    @Test
    void testLifecycleHooks() {
        AtomicInteger beforeAttempt = new AtomicInteger(0);
        AtomicInteger afterSuccess = new AtomicInteger(0);
        AtomicInteger afterFailure = new AtomicInteger(0);

        AttemptLifecycle<String> lifecycle = new AttemptLifecycle<>() {
            @Override
            public void beforeAttempt(RetryContext<String> ctx) {
                beforeAttempt.incrementAndGet();
            }

            @Override
            public void afterSuccess(RetryContext<String> ctx) {
                afterSuccess.incrementAndGet();
            }

            @Override
            public void afterFailure(RetryContext<String> ctx, Throwable error) {
                afterFailure.incrementAndGet();
            }
        };

        String result = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryOn(RuntimeException.class)
                .lifecycle(lifecycle)
                .execute((Callable<String>) () -> {
                    if (beforeAttempt.get() < 2) {
                        throw new RuntimeException("retry");
                    }
                    return "success";
                })
                .getResult();

        assertEquals("success", result);
        assertTrue(beforeAttempt.get() >= 2);
        assertEquals(1, afterSuccess.get());
        assertTrue(afterFailure.get() >= 1);
    }
}

