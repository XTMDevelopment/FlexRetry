package id.xtramile.flexretry.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.config.RetryConfig;
import id.xtramile.flexretry.config.RetryTemplate;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RetryTemplate usage
 */
class RetryTemplateIntegrationTest {

    @Test
    void testRetryTemplate() {
        RetryConfig<String> config = Retry.<String>newBuilder()
                .name("template-config")
                .maxAttempts(3)
                .retryOn(RuntimeException.class)
                .execute((Callable<String>) () -> "test")
                .toConfig();

        RetryTemplate<String> template = Retry.template(config);
        assertNotNull(template);

        AtomicInteger attempts = new AtomicInteger(0);
        String result = template.run(() -> {
            attempts.incrementAndGet();

            if (attempts.get() < 2) {
                throw new RuntimeException("retry");
            }

            return "success";
        });

        assertEquals("success", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void testRetryTemplateWithDifferentTasks() {
        RetryConfig<String> config = Retry.<String>newBuilder()
                .name("reusable-template")
                .maxAttempts(3)
                .retryOn(RuntimeException.class)
                .execute((Callable<String>) () -> "dummy")
                .toConfig();

        RetryTemplate<String> template = Retry.template(config);

        String result1 = template.run(() -> "task1");
        String result2 = template.run(() -> "task2");

        assertEquals("task1", result1);
        assertEquals("task2", result2);
    }
}

