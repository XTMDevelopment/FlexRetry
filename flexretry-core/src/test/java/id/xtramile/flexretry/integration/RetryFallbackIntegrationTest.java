package id.xtramile.flexretry.integration;

import id.xtramile.flexretry.Retry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for fallback scenarios
 */
class RetryFallbackIntegrationTest {

    @Test
    void testFallbackOnExhaustion() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
                .maxAttempts(2)
                .retryOn(RuntimeException.class)
                .fallback(error -> "fallback-value")
                .execute((Callable<String>) () -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("always fail");
                })
                .getResult();

        assertEquals("fallback-value", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void testFallbackWithErrorType() {
        AtomicReference<Throwable> capturedError = new AtomicReference<>();

        String result = Retry.<String>newBuilder()
                .maxAttempts(1)
                .fallback(error -> {
                    capturedError.set(error);

                    Throwable cause = error.getCause();
                    String errorName = cause != null ? cause.getClass().getSimpleName() : error.getClass().getSimpleName();
                    return "fallback-" + errorName;
                })
                .execute((Callable<String>) () -> {
                    throw new IllegalArgumentException("test error");
                })
                .getResult();

        assertEquals("fallback-IllegalArgumentException", result);
        assertNotNull(capturedError.get());
    }
}

