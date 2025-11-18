package id.xtramile.flexretry.strategy.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompositeRetryPolicyTest {

    @Test
    void testConstructorWithSinglePolicy() {
        RetryPolicy<String> policy1 = (result, error, attempt, maxAttempts) -> true;

        CompositeRetryPolicy<String> composite = new CompositeRetryPolicy<>(policy1);

        assertNotNull(composite);
    }

    @Test
    void testConstructorWithMultiplePolicies() {
        RetryPolicy<String> policy1 = (result, error, attempt, maxAttempts) -> false;
        RetryPolicy<String> policy2 = (result, error, attempt, maxAttempts) -> true;

        CompositeRetryPolicy<String> composite = new CompositeRetryPolicy<>(policy1, policy2);

        assertNotNull(composite);
    }

    @Test
    void testShouldRetryWhenAnyPolicyReturnsTrue() {
        RetryPolicy<String> policy1 = (result, error, attempt, maxAttempts) -> false;
        RetryPolicy<String> policy2 = (result, error, attempt, maxAttempts) -> true;
        RetryPolicy<String> policy3 = (result, error, attempt, maxAttempts) -> false;

        CompositeRetryPolicy<String> composite = new CompositeRetryPolicy<>(policy1, policy2, policy3);

        assertTrue(composite.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testShouldRetryWhenAllPoliciesReturnFalse() {
        RetryPolicy<String> policy1 = (result, error, attempt, maxAttempts) -> false;
        RetryPolicy<String> policy2 = (result, error, attempt, maxAttempts) -> false;
        RetryPolicy<String> policy3 = (result, error, attempt, maxAttempts) -> false;

        CompositeRetryPolicy<String> composite = new CompositeRetryPolicy<>(policy1, policy2, policy3);

        assertFalse(composite.shouldRetry("result", null, 1, 5));
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testShouldRetryWithNullPolicy() {
        RetryPolicy<String> policy1 = (result, error, attempt, maxAttempts) -> false;
        RetryPolicy<String> policy2 = null;
        RetryPolicy<String> policy3 = (result, error, attempt, maxAttempts) -> true;

        CompositeRetryPolicy<String> composite = new CompositeRetryPolicy<>(policy1, policy2, policy3);

        assertTrue(composite.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testShouldRetryWithAllNullPolicies() {
        CompositeRetryPolicy<String> composite = new CompositeRetryPolicy<>((RetryPolicy<String>) null);

        assertFalse(composite.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testShouldRetryWithFirstPolicyTrue() {
        RetryPolicy<String> policy1 = (result, error, attempt, maxAttempts) -> true;
        RetryPolicy<String> policy2 = (result, error, attempt, maxAttempts) -> false;

        CompositeRetryPolicy<String> composite = new CompositeRetryPolicy<>(policy1, policy2);

        assertTrue(composite.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testShouldRetryWithLastPolicyTrue() {
        RetryPolicy<String> policy1 = (result, error, attempt, maxAttempts) -> false;
        RetryPolicy<String> policy2 = (result, error, attempt, maxAttempts) -> true;

        CompositeRetryPolicy<String> composite = new CompositeRetryPolicy<>(policy1, policy2);

        assertTrue(composite.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testShouldRetryWithEmptyArray() {
        CompositeRetryPolicy<String> composite = new CompositeRetryPolicy<>();

        assertFalse(composite.shouldRetry("result", null, 1, 5));
    }
}

