package id.xtramile.flexretry.integration;

import id.xtramile.flexretry.Retry;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for null safety
 */
class RetryNullSafetyIntegrationTest {

    @Test
    void testNullResultHandling() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryIf(Objects::isNull)
                .execute((Callable<String>) () -> {
                    attempts.incrementAndGet();

                    if (attempts.get() < 2) {
                        return null;
                    }

                    return "not-null";
                })
                .getResult();

        assertEquals("not-null", result);
        assertEquals(2, attempts.get());
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored", "ThrowableNotThrown"})
    @Test
    void testNullSafetyInCallbacks() {
        AtomicInteger callbackCount = new AtomicInteger(0);

        assertDoesNotThrow(() -> {
            Retry.<String>newBuilder()
                    .maxAttempts(1)
                    .onAttempt(ctx -> {
                        callbackCount.incrementAndGet();

                        ctx.attempt();
                        ctx.maxAttempts();
                        ctx.lastResult();
                        ctx.lastError();
                        ctx.nextDelay();
                    })
                    .execute((Callable<String>) () -> "test")
                    .getResult();
        });

        assertEquals(1, callbackCount.get());
    }
}

