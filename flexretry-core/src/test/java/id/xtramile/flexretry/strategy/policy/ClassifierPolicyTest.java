package id.xtramile.flexretry.strategy.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClassifierPolicyTest {

    @Test
    void testConstructor() {
        ClassifierPolicy.ResultClassifier<String> classifier = result -> ClassifierPolicy.Decision.SUCCESS;
        ClassifierPolicy<String> policy = new ClassifierPolicy<>(classifier);

        assertNotNull(policy);
    }

    @Test
    void testConstructorWithNullClassifier() {
        assertThrows(NullPointerException.class,
                () -> new ClassifierPolicy<>(null));
    }

    @Test
    void testShouldRetryWithError() {
        ClassifierPolicy.ResultClassifier<String> classifier = result -> ClassifierPolicy.Decision.RETRY;
        ClassifierPolicy<String> policy = new ClassifierPolicy<>(classifier);
        RuntimeException error = new RuntimeException("error");

        assertFalse(policy.shouldRetry("result", error, 1, 5));
    }

    @Test
    void testShouldRetryWhenAttemptExceedsMax() {
        ClassifierPolicy.ResultClassifier<String> classifier = result -> ClassifierPolicy.Decision.RETRY;
        ClassifierPolicy<String> policy = new ClassifierPolicy<>(classifier);

        assertFalse(policy.shouldRetry("result", null, 5, 5));
    }

    @Test
    void testShouldRetryWithRetryDecision() {
        ClassifierPolicy.ResultClassifier<String> classifier = result -> ClassifierPolicy.Decision.RETRY;
        ClassifierPolicy<String> policy = new ClassifierPolicy<>(classifier);

        assertTrue(policy.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testShouldRetryWithSuccessDecision() {
        ClassifierPolicy.ResultClassifier<String> classifier = result -> ClassifierPolicy.Decision.SUCCESS;
        ClassifierPolicy<String> policy = new ClassifierPolicy<>(classifier);

        assertFalse(policy.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testShouldRetryWithFailDecision() {
        ClassifierPolicy.ResultClassifier<String> classifier = result -> ClassifierPolicy.Decision.FAIL;
        ClassifierPolicy<String> policy = new ClassifierPolicy<>(classifier);

        assertFalse(policy.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testShouldRetryWithConditionalClassifier() {
        ClassifierPolicy.ResultClassifier<String> classifier = result -> {
            if (result == null || result.isEmpty()) {
                return ClassifierPolicy.Decision.RETRY;
            }

            return ClassifierPolicy.Decision.SUCCESS;
        };

        ClassifierPolicy<String> policy = new ClassifierPolicy<>(classifier);

        assertTrue(policy.shouldRetry(null, null, 1, 5));
        assertTrue(policy.shouldRetry("", null, 1, 5));
        assertFalse(policy.shouldRetry("success", null, 1, 5));
    }

    @Test
    void testShouldRetryAtMaxAttempts() {
        ClassifierPolicy.ResultClassifier<String> classifier = result -> ClassifierPolicy.Decision.RETRY;
        ClassifierPolicy<String> policy = new ClassifierPolicy<>(classifier);

        assertFalse(policy.shouldRetry("result", null, 3, 3));
    }

    @Test
    void testShouldRetryBeforeMaxAttempts() {
        ClassifierPolicy.ResultClassifier<String> classifier = result -> ClassifierPolicy.Decision.RETRY;
        ClassifierPolicy<String> policy = new ClassifierPolicy<>(classifier);

        assertTrue(policy.shouldRetry("result", null, 1, 3));
        assertTrue(policy.shouldRetry("result", null, 2, 3));
        assertFalse(policy.shouldRetry("result", null, 3, 3));
    }
}

