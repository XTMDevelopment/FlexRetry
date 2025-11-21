package id.xtramile.flexretry.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.exception.RetryException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for listener callbacks
 */
class RetryListenersIntegrationTest {

    @Test
    void testAllListenerCallbacks() {
        AtomicInteger onAttempt = new AtomicInteger(0);
        AtomicInteger afterAttemptSuccess = new AtomicInteger(0);
        AtomicInteger afterAttemptFailure = new AtomicInteger(0);
        AtomicInteger onSuccess = new AtomicInteger(0);
        AtomicInteger onFailure = new AtomicInteger(0);
        AtomicInteger onFinally = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryOn(RuntimeException.class)
                .onAttempt(ctx -> onAttempt.incrementAndGet())
                .afterAttemptSuccess((r, ctx) -> afterAttemptSuccess.incrementAndGet())
                .afterAttemptFailure((e, ctx) -> afterAttemptFailure.incrementAndGet())
                .onSuccess((r, ctx) -> onSuccess.incrementAndGet())
                .onFailure((e, ctx) -> onFailure.incrementAndGet())
                .onFinally(ctx -> onFinally.incrementAndGet())
                .execute((Callable<String>) () -> {
                    if (onAttempt.get() < 2) {
                        throw new RuntimeException("retry");
                    }

                    return "success";
                })
                .getResult();

        assertEquals("success", result);
        assertTrue(onAttempt.get() >= 2);
        assertEquals(1, afterAttemptSuccess.get());
        assertEquals(1, onAttempt.get() - 1, afterAttemptFailure.get());
        assertEquals(1, onSuccess.get());
        assertEquals(0, onFailure.get());
        assertEquals(1, onFinally.get());
    }

    @Test
    void testListenerCallbacksOnFailure() {
        AtomicInteger onAttempt = new AtomicInteger(0);
        AtomicInteger onFailure = new AtomicInteger(0);
        AtomicInteger onFinally = new AtomicInteger(0);

        assertThrows(RetryException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(2)
                    .retryOn(RuntimeException.class)
                    .onAttempt(ctx -> onAttempt.incrementAndGet())
                    .onFailure((e, ctx) -> onFailure.incrementAndGet())
                    .onFinally(ctx -> onFinally.incrementAndGet())
                    .execute((Callable<String>) () -> {
                        throw new RuntimeException("always fail");
                    })
                    .getResult());

        assertTrue(onAttempt.get() >= 2);
        assertEquals(1, onFailure.get());
        assertEquals(1, onFinally.get());
    }
}

