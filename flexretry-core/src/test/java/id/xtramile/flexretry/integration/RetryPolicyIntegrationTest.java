package id.xtramile.flexretry.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.strategy.policy.ClassifierPolicy;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for retry policy combinations
 */
class RetryPolicyIntegrationTest {

    @Test
    void testMultipleRetryPoliciesOr() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
                .maxAttempts(5)
                .retryOn(RuntimeException.class)
                .retryIf(Objects::isNull)
                .retryIf(r -> r != null && r.equals("retry"))
                .execute((Callable<String>) () -> {
                    attempts.incrementAndGet();

                    if (attempts.get() < 3) {
                        throw new RuntimeException("error");
                    }

                    return "success";
                })
                .getResult();

        assertEquals("success", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void testClassifierPolicyWithExceptionPolicy() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
                .maxAttempts(5)
                .retryOn(IllegalArgumentException.class)
                .classify(r -> {
                    if (r == null || r.equals("retry")) {
                        return ClassifierPolicy.Decision.RETRY;
                    }

                    return ClassifierPolicy.Decision.SUCCESS;
                })
                .execute((Callable<String>) () -> {
                    attempts.incrementAndGet();

                    if (attempts.get() == 1) {
                        throw new IllegalArgumentException("error");
                    }

                    if (attempts.get() == 2) {
                        return "retry";
                    }

                    return "success";
                })
                .getResult();

        assertEquals("success", result);
        assertEquals(3, attempts.get());
    }
}

