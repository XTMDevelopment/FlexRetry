package id.xtramile.flexretry.exception;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class AttemptTimeoutExceptionTest {

    @Test
    void testAttemptTimeoutExceptionWithMessageAndCause() {
        String message = "Attempt timed out after 100ms";
        TimeoutException cause = new TimeoutException("Task execution timeout");

        AttemptTimeoutException exception = new AttemptTimeoutException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertInstanceOf(TimeoutException.class, exception.getCause());
    }

    @Test
    void testAttemptTimeoutExceptionWithMessageOnly() {
        String message = "Attempt timed out after 200ms";

        AttemptTimeoutException exception = new AttemptTimeoutException(message, null);

        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testAttemptTimeoutExceptionExtendsRuntimeException() {
        AttemptTimeoutException exception = new AttemptTimeoutException("Test", null);

        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    void testAttemptTimeoutExceptionWithNestedCause() {
        String message = "Attempt timed out after 150ms";
        RuntimeException nestedCause = new RuntimeException("Nested error");
        TimeoutException timeoutCause = new TimeoutException("Timeout");
        timeoutCause.initCause(nestedCause);

        AttemptTimeoutException exception = new AttemptTimeoutException(message, timeoutCause);

        assertEquals(message, exception.getMessage());
        assertEquals(timeoutCause, exception.getCause());
        assertEquals(nestedCause, exception.getCause().getCause());
    }

    @Test
    void testAttemptTimeoutExceptionCanBeThrown() {
        String message = "Test timeout exception";

        assertThrows(AttemptTimeoutException.class, () -> {
            throw new AttemptTimeoutException(message, null);
        });
    }

    @Test
    void testAttemptTimeoutExceptionPreservesTimeoutException() {
        TimeoutException originalTimeout = new TimeoutException("Original timeout");
        AttemptTimeoutException exception = new AttemptTimeoutException("Wrapped timeout", originalTimeout);

        Throwable cause = exception.getCause();
        assertNotNull(cause);
        assertEquals(TimeoutException.class, cause.getClass());
        assertEquals("Original timeout", cause.getMessage());
    }
}

