package id.xtramile.flexretry.strategy.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PoliciesTest {

    @Test
    void testOrWithSinglePolicy() {
        RetryPolicy<String> policy = (result, error, attempt, maxAttempts) -> true;
        RetryPolicy<String> orPolicy = Policies.or(policy);

        assertTrue(orPolicy.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testOrWithMultiplePolicies() {
        RetryPolicy<String> policy1 = (result, error, attempt, maxAttempts) -> false;
        RetryPolicy<String> policy2 = (result, error, attempt, maxAttempts) -> true;
        RetryPolicy<String> policy3 = (result, error, attempt, maxAttempts) -> false;

        RetryPolicy<String> orPolicy = Policies.or(policy1, policy2, policy3);

        assertTrue(orPolicy.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testOrWithAllFalse() {
        RetryPolicy<String> policy1 = (result, error, attempt, maxAttempts) -> false;
        RetryPolicy<String> policy2 = (result, error, attempt, maxAttempts) -> false;

        RetryPolicy<String> orPolicy = Policies.or(policy1, policy2);

        assertFalse(orPolicy.shouldRetry("result", null, 1, 5));
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testOrWithNullPolicy() {
        RetryPolicy<String> policy1 = (result, error, attempt, maxAttempts) -> false;
        RetryPolicy<String> policy2 = null;
        RetryPolicy<String> policy3 = (result, error, attempt, maxAttempts) -> true;

        RetryPolicy<String> orPolicy = Policies.or(policy1, policy2, policy3);

        assertTrue(orPolicy.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testAndWithSinglePolicy() {
        RetryPolicy<String> policy = (result, error, attempt, maxAttempts) -> true;
        RetryPolicy<String> andPolicy = Policies.and(policy);

        assertFalse(andPolicy.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testAndWithMultiplePolicies() {
        RetryPolicy<String> policy1 = (result, error, attempt, maxAttempts) -> false;
        RetryPolicy<String> policy2 = (result, error, attempt, maxAttempts) -> false;
        RetryPolicy<String> policy3 = (result, error, attempt, maxAttempts) -> false;

        RetryPolicy<String> andPolicy = Policies.and(policy1, policy2, policy3);

        assertTrue(andPolicy.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testAndWithOneTrue() {
        RetryPolicy<String> policy1 = (result, error, attempt, maxAttempts) -> false;
        RetryPolicy<String> policy2 = (result, error, attempt, maxAttempts) -> true;
        RetryPolicy<String> policy3 = (result, error, attempt, maxAttempts) -> false;

        RetryPolicy<String> andPolicy = Policies.and(policy1, policy2, policy3);

        assertFalse(andPolicy.shouldRetry("result", null, 1, 5));
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void testAndWithNullPolicy() {
        RetryPolicy<String> policy1 = (result, error, attempt, maxAttempts) -> false;
        RetryPolicy<String> policy2 = null;
        RetryPolicy<String> policy3 = (result, error, attempt, maxAttempts) -> false;

        RetryPolicy<String> andPolicy = Policies.and(policy1, policy2, policy3);

        assertTrue(andPolicy.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testNotWithTruePolicy() {
        RetryPolicy<String> policy = (result, error, attempt, maxAttempts) -> true;
        RetryPolicy<String> notPolicy = Policies.not(policy);

        assertFalse(notPolicy.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testNotWithFalsePolicy() {
        RetryPolicy<String> policy = (result, error, attempt, maxAttempts) -> false;
        RetryPolicy<String> notPolicy = Policies.not(policy);

        assertTrue(notPolicy.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testNotWithNullPolicy() {
        RetryPolicy<String> notPolicy = Policies.not(null);

        assertTrue(notPolicy.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testOrWithEmptyArray() {
        RetryPolicy<String> orPolicy = Policies.or();

        assertFalse(orPolicy.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testAndWithEmptyArray() {
        RetryPolicy<String> andPolicy = Policies.and();

        assertTrue(andPolicy.shouldRetry("result", null, 1, 5));
    }
}

