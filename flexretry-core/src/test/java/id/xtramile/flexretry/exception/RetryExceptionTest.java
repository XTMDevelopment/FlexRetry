package id.xtramile.flexretry.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RetryExceptionTest {

    @Test
    void testRetryExceptionWithMessageAndCause() {
        String message = "Retry failed";
        Throwable cause = new RuntimeException("Original error");
        int attempts = 5;

        RetryException exception = new RetryException(message, cause, attempts);

        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals(attempts, exception.attempts());
    }

    @Test
    void testRetryExceptionWithMessageOnly() {
        String message = "Retry exhausted";
        int attempts = 3;

        RetryException exception = new RetryException(message, null, attempts);

        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
        assertEquals(attempts, exception.attempts());
    }

    @Test
    void testRetryExceptionWithZeroAttempts() {
        RetryException exception = new RetryException("No attempts", null, 0);

        assertEquals(0, exception.attempts());
    }
}

