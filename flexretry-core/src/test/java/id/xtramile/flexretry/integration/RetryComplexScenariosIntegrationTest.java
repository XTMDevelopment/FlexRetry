package id.xtramile.flexretry.integration;

import id.xtramile.flexretry.Retry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for complex retry scenarios
 */
class RetryComplexScenariosIntegrationTest {

    @Test
    void testComplexRetryScenario() {
        AtomicInteger attempts = new AtomicInteger(0);
        List<String> events = new ArrayList<>();

        String result = Retry.<String>newBuilder()
                .name("complex-scenario")
                .id("scenario-1")
                .tag("test", true)
                .maxAttempts(5)
                .backoff(new id.xtramile.flexretry.strategy.backoff.ExponentialBackoff(Duration.ofMillis(10), 1.5))
                .retryOn(RuntimeException.class, IllegalArgumentException.class)
                .retryIf(r -> r != null && r.equals("retry"))
                .onAttempt(ctx -> events.add("attempt-" + ctx.attempt()))
                .afterAttemptSuccess((r, ctx) -> events.add("success-attempt-" + ctx.attempt()))
                .afterAttemptFailure((e, ctx) -> events.add("failure-attempt-" + ctx.attempt()))
                .onSuccess((r, ctx) -> events.add("final-success"))
                .onFinally(ctx -> events.add("finally"))
                .beforeSleep((duration, ctx) -> {
                    events.add("sleep-" + duration.toMillis() + "ms");
                    return duration;
                })
                .fallback(error -> "fallback")
                .execute((Callable<String>) () -> {
                    attempts.incrementAndGet();
                    int attempt = attempts.get();

                    if (attempt == 1) {
                        throw new RuntimeException("error1");
                    }

                    if (attempt == 2) {
                        return "retry";
                    }

                    if (attempt == 3) {
                        throw new IllegalArgumentException("error2");
                    }

                    return "success";
                })
                .getResult();

        assertEquals("success", result);
        assertTrue(attempts.get() >= 3);
        assertTrue(events.contains("finally"));
    }

    @Test
    void testMultipleMethodChains() {
        Retry.Builder<String> baseBuilder = Retry.<String>newBuilder()
                .name("base")
                .maxAttempts(3);

        String result1 = baseBuilder
                .retryOn(RuntimeException.class)
                .execute((Callable<String>) () -> "result1")
                .getResult();

        String result2 = baseBuilder
                .retryIf(Objects::isNull)
                .execute((Callable<String>) () -> "result2")
                .getResult();

        assertEquals("result1", result1);
        assertEquals("result2", result2);
    }
}

