package id.xtramile.flexretry.observability.events;

import id.xtramile.flexretry.RetryContext;
import id.xtramile.flexretry.RetryOutcome;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RetryEventTest {

    @Test
    void testBuilder_WithType() {
        RetryEvent<String> event = RetryEvent.<String>builder(RetryEventType.RETRY_ATTEMPT)
                .build();

        assertNotNull(event);
        assertEquals(RetryEventType.RETRY_ATTEMPT, event.getType());
    }

    @Test
    void testBuilder_WithAllFields() {
        RetryContext<String> context = new RetryContext<>(
                "test-id", "test-name", 2, 3, "result", null, Duration.ofMillis(100), null);
        RuntimeException error = new RuntimeException("test error");
        RetryOutcome<String> outcome = new RetryOutcome<>(true, "result", null, 2);
        Instant timestamp = Instant.now();

        RetryEvent<String> event = RetryEvent.<String>builder(RetryEventType.RETRY_SUCCESS)
                .context(context)
                .attempt(2)
                .lastError(error)
                .nextDelay(Duration.ofMillis(200))
                .outcome(outcome)
                .timestamp(timestamp)
                .build();

        assertEquals(RetryEventType.RETRY_SUCCESS, event.getType());
        assertEquals(context, event.getContext());
        assertEquals(2, event.getAttempt());
        assertEquals(error, event.getLastError());
        assertEquals(Duration.ofMillis(200), event.getNextDelay());
        assertEquals(outcome, event.getOutcome());
        assertEquals(timestamp, event.getTimestamp());
    }

    @Test
    void testBuilder_WithNullFields() {
        RetryEvent<String> event = RetryEvent.<String>builder(RetryEventType.RETRY_ATTEMPT)
                .context(null)
                .attempt(1)
                .lastError(null)
                .nextDelay(null)
                .outcome(null)
                .timestamp(null)
                .build();

        assertNotNull(event);
        assertEquals(RetryEventType.RETRY_ATTEMPT, event.getType());
        assertNull(event.getContext());
        assertEquals(1, event.getAttempt());
        assertNull(event.getLastError());
        assertNull(event.getNextDelay());
        assertNull(event.getOutcome());
        assertNotNull(event.getTimestamp());
    }

    @Test
    void testBuilder_AutoGeneratesTimestamp() {
        Instant before = Instant.now();

        RetryEvent<String> event = RetryEvent.<String>builder(RetryEventType.RETRY_ATTEMPT)
                .build();

        Instant after = Instant.now();

        assertNotNull(event.getTimestamp());
        assertTrue(event.getTimestamp().isAfter(before.minusSeconds(1)) ||
                event.getTimestamp().equals(before));
        assertTrue(event.getTimestamp().isBefore(after.plusSeconds(1)) ||
                event.getTimestamp().equals(after));
    }

    @Test
    void testBuilder_WithCustomTimestamp() {
        Instant customTimestamp = Instant.parse("2023-01-01T00:00:00Z");

        RetryEvent<String> event = RetryEvent.<String>builder(RetryEventType.RETRY_ATTEMPT)
                .timestamp(customTimestamp)
                .build();

        assertEquals(customTimestamp, event.getTimestamp());
    }

    @Test
    void testGetters_AllEventTypes() {
        for (RetryEventType type : RetryEventType.values()) {
            RetryEvent<String> event = RetryEvent.<String>builder(type)
                    .attempt(1)
                    .build();

            assertEquals(type, event.getType());
            assertEquals(1, event.getAttempt());
        }
    }

    @Test
    void testBuilder_Chaining() {
        RetryContext<String> context = new RetryContext<>(
                "id", "name", 1, 3, null, null, Duration.ZERO, null);

        RetryEvent<String> event = RetryEvent.<String>builder(RetryEventType.RETRY_FAILURE)
                .context(context)
                .attempt(1)
                .lastError(new RuntimeException("error"))
                .nextDelay(Duration.ofMillis(100))
                .outcome(new RetryOutcome<>(false, null, new RuntimeException("error"), 1))
                .build();

        assertNotNull(event);
        assertEquals(RetryEventType.RETRY_FAILURE, event.getType());
        assertEquals(context, event.getContext());
        assertEquals(1, event.getAttempt());
        assertNotNull(event.getLastError());
        assertEquals(Duration.ofMillis(100), event.getNextDelay());
        assertNotNull(event.getOutcome());
    }

    @Test
    void testBuilder_WithDifferentGenericTypes() {
        RetryEvent<String> stringEvent = RetryEvent.<String>builder(RetryEventType.RETRY_ATTEMPT)
                .build();

        RetryEvent<Integer> intEvent = RetryEvent.<Integer>builder(RetryEventType.RETRY_ATTEMPT)
                .build();

        assertNotNull(stringEvent);
        assertNotNull(intEvent);
        assertEquals(RetryEventType.RETRY_ATTEMPT, stringEvent.getType());
        assertEquals(RetryEventType.RETRY_ATTEMPT, intEvent.getType());
    }
}

