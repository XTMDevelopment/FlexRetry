package id.xtramile.flexretry.strategy.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaxSameErrorPolicyTest {

    @Test
    void testConstructorWithValidLimit() {
        MaxSameErrorPolicy<String> policy = new MaxSameErrorPolicy<>(3);

        assertNotNull(policy);
    }

    @Test
    void testConstructorWithInvalidLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> new MaxSameErrorPolicy<>(0));

        assertThrows(IllegalArgumentException.class,
                () -> new MaxSameErrorPolicy<>(-1));
    }

    @Test
    void testShouldRetryWithNullError() {
        MaxSameErrorPolicy<String> policy = new MaxSameErrorPolicy<>(3);

        assertFalse(policy.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testShouldRetryWhenAttemptExceedsMax() {
        MaxSameErrorPolicy<String> policy = new MaxSameErrorPolicy<>(3);
        RuntimeException error = new RuntimeException("error");

        assertFalse(policy.shouldRetry(null, error, 5, 5));
    }

    @Test
    void testShouldRetryWithSameError() {
        MaxSameErrorPolicy<String> policy = new MaxSameErrorPolicy<>(3);
        RuntimeException error = new RuntimeException("error");

        assertTrue(policy.shouldRetry(null, error, 1, 5));
        assertTrue(policy.shouldRetry(null, error, 2, 5));
        assertFalse(policy.shouldRetry(null, error, 3, 5));
    }

    @Test
    void testShouldRetryWithDifferentErrors() {
        MaxSameErrorPolicy<String> policy = new MaxSameErrorPolicy<>(2);
        RuntimeException error1 = new RuntimeException("error1");
        IllegalArgumentException error2 = new IllegalArgumentException("error2");

        assertTrue(policy.shouldRetry(null, error1, 1, 5));
        assertTrue(policy.shouldRetry(null, error2, 2, 5));
        assertFalse(policy.shouldRetry(null, error2, 3, 5));
    }

    @Test
    void testShouldRetryWithLimitOne() {
        MaxSameErrorPolicy<String> policy = new MaxSameErrorPolicy<>(1);
        RuntimeException error = new RuntimeException("error");

        assertFalse(policy.shouldRetry(null, error, 1, 5));
    }

    @Test
    void testShouldRetryWithLimitTwo() {
        MaxSameErrorPolicy<String> policy = new MaxSameErrorPolicy<>(2);
        RuntimeException error = new RuntimeException("error");

        assertTrue(policy.shouldRetry(null, error, 1, 5));
        assertFalse(policy.shouldRetry(null, error, 2, 5));
    }
}

