package id.xtramile.flexretry.strategy.policy;

import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class ResultPredicateRetryPolicyTest {

    @Test
    void testConstructor() {
        Predicate<String> predicate = Objects::isNull;
        ResultPredicateRetryPolicy<String> policy = new ResultPredicateRetryPolicy<>(predicate);

        assertNotNull(policy);
    }

    @Test
    void testConstructorWithNullPredicate() {
        assertThrows(NullPointerException.class,
                () -> new ResultPredicateRetryPolicy<>(null));
    }

    @Test
    void testShouldRetryWithError() {
        Predicate<String> predicate = Objects::isNull;
        ResultPredicateRetryPolicy<String> policy = new ResultPredicateRetryPolicy<>(predicate);
        RuntimeException error = new RuntimeException("error");

        assertFalse(policy.shouldRetry("result", error, 1, 5));
    }

    @Test
    void testShouldRetryWhenAttemptExceedsMax() {
        Predicate<String> predicate = s -> true;
        ResultPredicateRetryPolicy<String> policy = new ResultPredicateRetryPolicy<>(predicate);

        assertFalse(policy.shouldRetry("result", null, 5, 5));
    }

    @Test
    void testShouldRetryWithMatchingPredicate() {
        Predicate<String> predicate = Objects::isNull;
        ResultPredicateRetryPolicy<String> policy = new ResultPredicateRetryPolicy<>(predicate);

        assertTrue(policy.shouldRetry(null, null, 1, 5));
    }

    @Test
    void testShouldRetryWithNonMatchingPredicate() {
        Predicate<String> predicate = Objects::isNull;
        ResultPredicateRetryPolicy<String> policy = new ResultPredicateRetryPolicy<>(predicate);

        assertFalse(policy.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testShouldRetryWithEmptyStringPredicate() {
        Predicate<String> predicate = s -> s != null && s.isEmpty();
        ResultPredicateRetryPolicy<String> policy = new ResultPredicateRetryPolicy<>(predicate);

        assertTrue(policy.shouldRetry("", null, 1, 5));
        assertFalse(policy.shouldRetry("non-empty", null, 1, 5));
    }

    @Test
    void testShouldRetryAtMaxAttempts() {
        Predicate<String> predicate = s -> true;
        ResultPredicateRetryPolicy<String> policy = new ResultPredicateRetryPolicy<>(predicate);

        assertFalse(policy.shouldRetry("result", null, 3, 3));
    }

    @Test
    void testShouldRetryBeforeMaxAttempts() {
        Predicate<String> predicate = Objects::isNull;
        ResultPredicateRetryPolicy<String> policy = new ResultPredicateRetryPolicy<>(predicate);

        assertTrue(policy.shouldRetry(null, null, 1, 3));
        assertTrue(policy.shouldRetry(null, null, 2, 3));
        assertFalse(policy.shouldRetry(null, null, 3, 3));
    }

    @Test
    void testShouldRetryWithComplexPredicate() {
        Predicate<Integer> predicate = i -> i != null && i < 100;
        ResultPredicateRetryPolicy<Integer> policy = new ResultPredicateRetryPolicy<>(predicate);

        assertTrue(policy.shouldRetry(50, null, 1, 5));
        assertTrue(policy.shouldRetry(99, null, 1, 5));
        assertFalse(policy.shouldRetry(100, null, 1, 5));
        assertFalse(policy.shouldRetry(200, null, 1, 5));
    }
}

