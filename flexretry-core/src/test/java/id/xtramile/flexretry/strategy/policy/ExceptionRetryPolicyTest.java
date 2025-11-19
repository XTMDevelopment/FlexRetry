package id.xtramile.flexretry.strategy.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionRetryPolicyTest {

    @Test
    void testConstructorWithSingleException() {
        ExceptionRetryPolicy<String> policy = new ExceptionRetryPolicy<>(RuntimeException.class);

        assertNotNull(policy);
    }

    @Test
    void testConstructorWithMultipleExceptions() {
        ExceptionRetryPolicy<String> policy = new ExceptionRetryPolicy<>(
                RuntimeException.class,
                IllegalArgumentException.class,
                IllegalStateException.class
        );

        assertNotNull(policy);
    }

    @Test
    void testShouldRetryWithNullError() {
        ExceptionRetryPolicy<String> policy = new ExceptionRetryPolicy<>(RuntimeException.class);

        assertFalse(policy.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testShouldRetryWhenAttemptExceedsMax() {
        ExceptionRetryPolicy<String> policy = new ExceptionRetryPolicy<>(RuntimeException.class);
        RuntimeException error = new RuntimeException("error");

        assertFalse(policy.shouldRetry(null, error, 5, 5));
    }

    @Test
    void testShouldRetryWithMatchingException() {
        ExceptionRetryPolicy<String> policy = new ExceptionRetryPolicy<>(RuntimeException.class);
        RuntimeException error = new RuntimeException("error");

        assertTrue(policy.shouldRetry(null, error, 1, 5));
    }

    @Test
    void testShouldRetryWithNonMatchingException() {
        ExceptionRetryPolicy<String> policy = new ExceptionRetryPolicy<>(IllegalStateException.class);
        IllegalArgumentException error = new IllegalArgumentException("error");

        assertFalse(policy.shouldRetry(null, error, 1, 5));
    }

    @Test
    void testShouldRetryWithSubclassException() {
        ExceptionRetryPolicy<String> policy = new ExceptionRetryPolicy<>(RuntimeException.class);
        IllegalArgumentException error = new IllegalArgumentException("error");

        assertTrue(policy.shouldRetry(null, error, 1, 5));
    }

    @Test
    void testShouldRetryWithMultipleExceptionTypes() {
        ExceptionRetryPolicy<String> policy = new ExceptionRetryPolicy<>(
                RuntimeException.class,
                NullPointerException.class
        );

        RuntimeException error1 = new RuntimeException("error1");
        NullPointerException error2 = new NullPointerException("error2");
        IllegalArgumentException error3 = new IllegalArgumentException("error3");

        assertTrue(policy.shouldRetry(null, error1, 1, 5));
        assertTrue(policy.shouldRetry(null, error2, 1, 5));
        assertTrue(policy.shouldRetry(null, error3, 1, 5)); // IllegalArgumentException extends RuntimeException
    }

    @Test
    void testShouldRetryWithEmptyExceptionList() {
        ExceptionRetryPolicy<String> policy = new ExceptionRetryPolicy<>();
        RuntimeException error = new RuntimeException("error");

        assertFalse(policy.shouldRetry(null, error, 1, 5));
    }

    @Test
    void testShouldRetryAtMaxAttempts() {
        ExceptionRetryPolicy<String> policy = new ExceptionRetryPolicy<>(RuntimeException.class);
        RuntimeException error = new RuntimeException("error");

        assertFalse(policy.shouldRetry(null, error, 3, 3));
    }

    @Test
    void testShouldRetryBeforeMaxAttempts() {
        ExceptionRetryPolicy<String> policy = new ExceptionRetryPolicy<>(RuntimeException.class);
        RuntimeException error = new RuntimeException("error");

        assertTrue(policy.shouldRetry(null, error, 1, 3));
        assertTrue(policy.shouldRetry(null, error, 2, 3));
        assertFalse(policy.shouldRetry(null, error, 3, 3));
    }
}

