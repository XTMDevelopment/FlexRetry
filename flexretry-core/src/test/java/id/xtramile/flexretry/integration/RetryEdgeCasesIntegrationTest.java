package id.xtramile.flexretry.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.RetryException;
import id.xtramile.flexretry.RetryOutcome;
import id.xtramile.flexretry.config.RetryConfig;
import id.xtramile.flexretry.strategy.policy.ExceptionMappingPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for edge cases
 */
class RetryEdgeCasesIntegrationTest {

    @Test
    void testZeroMaxAttempts() {
        assertThrows(IllegalArgumentException.class,
                () -> Retry.<String>newBuilder()
                        .maxAttempts(0)
                        .execute((Callable<String>) () -> "test")
                        .getResult());
    }

    @Test
    void testNegativeDelayMillis() {
        RetryConfig<String> config = Retry.<String>newBuilder()
                .delayMillis(-100)
                .execute((Callable<String>) () -> "test")
                .toConfig();

        assertEquals(Duration.ZERO, config.backoff.delayForAttempt(1));
    }

    @Test
    void testMultipleTags() {
        RetryConfig<String> config = Retry.<String>newBuilder()
                .tag("env", "test")
                .tag("version", "1.0")
                .tag("region", "us-east")
                .execute((Callable<String>) () -> "test")
                .toConfig();

        assertEquals("test", config.tags.get("env"));
        assertEquals("1.0", config.tags.get("version"));
        assertEquals("us-east", config.tags.get("region"));
    }

    @Test
    void testReuseBuilder() {
        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(3)
                .retryOn(RuntimeException.class);

        String result1 = builder
                .execute((Callable<String>) () -> "result1")
                .getResult();

        String result2 = builder
                .execute((Callable<String>) () -> "result2")
                .getResult();

        assertEquals("result1", result1);
        assertEquals("result2", result2);
    }

    @Test
    void testRetryOnWithNoArguments() {
        RetryConfig<String> config = Retry.<String>newBuilder()
                .retryOn()
                .execute((Callable<String>) () -> "test")
                .toConfig();

        assertNotNull(config.policy);
        assertFalse(config.policy.shouldRetry(null, new RuntimeException("test"), 1, 3));
    }

    @Test
    void testExceptionMappingPolicyViaBuilder() {
        ExceptionMappingPolicy<String> policy =
                new ExceptionMappingPolicy<String>()
                        .when(e -> e instanceof IllegalArgumentException, ExceptionMappingPolicy.Decision.RETRY)
                        .when(e -> e instanceof IllegalStateException, ExceptionMappingPolicy.Decision.FAIL)
                        .otherwise(ExceptionMappingPolicy.Decision.IGNORE);

        assertThrows(RetryException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(2)
                    .policy(policy)
                    .execute((Callable<String>) () -> {
                        throw new IllegalArgumentException("retry");
                    })
                    .getResult());
    }

    @Test
    void testInterruptedExceptionAtBuilderLevel() {
        AtomicInteger attempts = new AtomicInteger(0);
        
        RetryOutcome<String> outcome = Retry.<String>newBuilder()
                .maxAttempts(3)
                .delayMillis(10)
                .retryOn(RuntimeException.class)
                .execute((Callable<String>) () -> {
                    attempts.incrementAndGet();

                    if (attempts.get() == 1) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedException("interrupted");
                    }

                    return "success";
                })
                .getOutcome();

        assertFalse(outcome.isSuccess());
        assertInstanceOf(InterruptedException.class, outcome.error());
    }
}

