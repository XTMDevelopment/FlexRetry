package id.xtramile.flexretry;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RetryContextTest {

    @Test
    void testConstructorWithAllParameters() {
        String id = "test-id";
        String name = "test-name";
        int attempt = 1;
        int maxAttempts = 3;
        String lastResult = "result";
        Throwable lastError = new RuntimeException("error");
        Duration nextDelay = Duration.ofMillis(100);
        Map<String, Object> tags = new HashMap<>();
        tags.put("key", "value");

        RetryContext<String> context = new RetryContext<>(
                id, name, attempt, maxAttempts, lastResult, lastError, nextDelay, tags
        );

        assertEquals(id, context.id());
        assertEquals(name, context.name());
        assertEquals(attempt, context.attempt());
        assertEquals(maxAttempts, context.maxAttempts());
        assertEquals(lastResult, context.lastResult());
        assertEquals(lastError, context.lastError());
        assertEquals(nextDelay, context.nextDelay());
        assertEquals(tags, context.tags());
    }

    @Test
    void testConstructorWithNullNextDelay() {
        RetryContext<String> context = new RetryContext<>(
                "id", "name", 1, 3, null, null, null, null
        );

        assertEquals(Duration.ZERO, context.nextDelay());
    }

    @Test
    void testConstructorWithNullTags() {
        RetryContext<String> context = new RetryContext<>(
                "id", "name", 1, 3, null, null, Duration.ZERO, null
        );

        assertNotNull(context.tags());
        assertTrue(context.tags().isEmpty());
    }

    @Test
    void testConstructorWithEmptyTags() {
        Map<String, Object> emptyTags = new HashMap<>();
        RetryContext<String> context = new RetryContext<>(
                "id", "name", 1, 3, null, null, Duration.ZERO, emptyTags
        );

        assertEquals(emptyTags, context.tags());
    }
}

