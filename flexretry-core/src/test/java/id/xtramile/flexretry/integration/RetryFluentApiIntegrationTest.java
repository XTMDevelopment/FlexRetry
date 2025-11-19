package id.xtramile.flexretry.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.RetryContext;
import id.xtramile.flexretry.config.RetryConfig;
import id.xtramile.flexretry.lifecycle.AttemptLifecycle;
import id.xtramile.flexretry.strategy.backoff.ExponentialBackoff;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Retry fluent API method chaining
 */
class RetryFluentApiIntegrationTest {

    @Test
    void testComplexFluentChain() {
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
                .name("complex-retry")
                .id("test-id-123")
                .tag("environment", "test")
                .tag("service", "api")
                .maxAttempts(5)
                .delayMillis(50)
                .retryOn(RuntimeException.class, IllegalArgumentException.class)
                .retryIf(Objects::isNull)
                .onAttempt(ctx -> attempts.incrementAndGet())
                .onSuccess((r, ctx) -> successCount.incrementAndGet())
                .onFailure((e, ctx) -> failureCount.incrementAndGet())
                .onFinally(ctx -> {})
                .beforeSleep((duration, ctx) -> duration)
                .fallback(error -> "fallback-result")
                .execute((Callable<String>) () -> {
                    int attempt = attempts.incrementAndGet();
                    if (attempt < 3) {
                        throw new RuntimeException("retry");
                    }
                    return "success";
                })
                .getResult();

        assertEquals("success", result);
        assertTrue(attempts.get() >= 3);
        assertEquals(1, successCount.get());
        assertEquals(0, failureCount.get());
    }

    @Test
    void testFullConfigurationChain() {
        AtomicInteger lifecycleBefore = new AtomicInteger(0);
        AtomicInteger lifecycleAfter = new AtomicInteger(0);

        AttemptLifecycle<String> lifecycle = createLifecycle(lifecycleBefore, lifecycleAfter);

        RetryConfig<String> config = Retry.<String>newBuilder()
                .name("full-config")
                .id("config-id")
                .tag("version", "1.0")
                .maxAttempts(3)
                .backoff(new ExponentialBackoff(Duration.ofMillis(10), 2.0))
                .retryOn(RuntimeException.class)
                .onAttempt(ctx -> {})
                .onSuccess((r, ctx) -> {})
                .onFailure((e, ctx) -> {})
                .onFinally(ctx -> {})
                .lifecycle(lifecycle)
                .fallback(error -> "fallback")
                .execute((Callable<String>) () -> "test")
                .toConfig();

        assertNotNull(config);
        assertEquals("full-config", config.name);
        assertEquals("config-id", config.id);
        assertEquals(3, config.stop.maxAttempts().orElse(0));
    }

    private AttemptLifecycle<String> createLifecycle(
            AtomicInteger lifecycleBefore, AtomicInteger lifecycleAfter) {

        return new AttemptLifecycle<>() {
            @Override
            public void beforeAttempt(RetryContext<String> ctx) {
                lifecycleBefore.incrementAndGet();
            }

            @Override
            public void afterSuccess(RetryContext<String> ctx) {
                lifecycleAfter.incrementAndGet();
            }
        };
    }
}

