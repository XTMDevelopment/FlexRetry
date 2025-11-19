package id.xtramile.flexretry.strategy.policy;

import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionPredicatePolicyTest {

    @Test
    void testConstructor() {
        Predicate<Throwable> predicate = e -> e instanceof RuntimeException;
        ExceptionPredicatePolicy<String> policy = new ExceptionPredicatePolicy<>(predicate);

        assertNotNull(policy);
    }

    @Test
    void testConstructorWithNullPredicate() {
        assertThrows(NullPointerException.class,
                () -> new ExceptionPredicatePolicy<>(null));
    }

    @Test
    void testShouldRetryWithNullError() {
        Predicate<Throwable> predicate = e -> e instanceof RuntimeException;
        ExceptionPredicatePolicy<String> policy = new ExceptionPredicatePolicy<>(predicate);

        assertFalse(policy.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testShouldRetryWhenAttemptExceedsMax() {
        Predicate<Throwable> predicate = e -> true;
        ExceptionPredicatePolicy<String> policy = new ExceptionPredicatePolicy<>(predicate);
        RuntimeException error = new RuntimeException("error");

        assertFalse(policy.shouldRetry(null, error, 5, 5));
    }

    @Test
    void testShouldRetryWithMatchingPredicate() {
        Predicate<Throwable> predicate = e -> e instanceof RuntimeException;
        ExceptionPredicatePolicy<String> policy = new ExceptionPredicatePolicy<>(predicate);
        RuntimeException error = new RuntimeException("error");

        assertTrue(policy.shouldRetry(null, error, 1, 5));
    }

    @Test
    void testShouldRetryWithNonMatchingPredicate() {
        Predicate<Throwable> predicate = e -> e instanceof IllegalArgumentException;
        ExceptionPredicatePolicy<String> policy = new ExceptionPredicatePolicy<>(predicate);
        RuntimeException error = new RuntimeException("error");

        assertFalse(policy.shouldRetry(null, error, 1, 5));
    }

    @Test
    void testByCause() {
        Predicate<Throwable> predicate = ExceptionPredicatePolicy.byCause(IllegalArgumentException.class);
        ExceptionPredicatePolicy<String> policy = new ExceptionPredicatePolicy<>(predicate);

        IllegalArgumentException directError = new IllegalArgumentException("error");
        assertTrue(policy.shouldRetry(null, directError, 1, 5));

        RuntimeException wrappedError = new RuntimeException(new IllegalArgumentException("cause"));
        assertTrue(policy.shouldRetry(null, wrappedError, 1, 5));

        RuntimeException nestedError = new RuntimeException(
                new IllegalStateException(new IllegalArgumentException("nested"))
        );
        assertTrue(policy.shouldRetry(null, nestedError, 1, 5));

        RuntimeException noMatch = new RuntimeException("no match");
        assertFalse(policy.shouldRetry(null, noMatch, 1, 5));
    }

    @Test
    void testByCauseWithNullCause() {
        Predicate<Throwable> predicate = ExceptionPredicatePolicy.byCause(IllegalArgumentException.class);
        ExceptionPredicatePolicy<String> policy = new ExceptionPredicatePolicy<>(predicate);
        RuntimeException error = new RuntimeException("no cause");

        assertFalse(policy.shouldRetry(null, error, 1, 5));
    }

    @Test
    void testByCauseWithCircularCause() {
        Predicate<Throwable> predicate = ExceptionPredicatePolicy.byCause(IllegalArgumentException.class);
        ExceptionPredicatePolicy<String> policy = new ExceptionPredicatePolicy<>(predicate);

        IllegalArgumentException error1 = new IllegalArgumentException("error1");
        RuntimeException error2 = new RuntimeException(error1);

        assertTrue(policy.shouldRetry(null, error2, 1, 5));
    }

    @Test
    void testCustomPredicate() {
        Predicate<Throwable> predicate = e -> e.getMessage() != null && e.getMessage().contains("retry");
        ExceptionPredicatePolicy<String> policy = new ExceptionPredicatePolicy<>(predicate);

        RuntimeException error1 = new RuntimeException("should retry");
        RuntimeException error2 = new RuntimeException("should not");

        assertTrue(policy.shouldRetry(null, error1, 1, 5));
        assertFalse(policy.shouldRetry(null, error2, 1, 5));
    }
}

