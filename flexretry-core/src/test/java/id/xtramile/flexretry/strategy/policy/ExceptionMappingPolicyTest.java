package id.xtramile.flexretry.strategy.policy;

import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionMappingPolicyTest {

    @Test
    void testConstructor() {
        ExceptionMappingPolicy<String> policy = new ExceptionMappingPolicy<>();

        assertNotNull(policy);
    }

    @Test
    void testWhen() {
        ExceptionMappingPolicy<String> policy = new ExceptionMappingPolicy<>();
        Predicate<Throwable> predicate = e -> e instanceof RuntimeException;
        ExceptionMappingPolicy<String> result = policy.when(predicate, ExceptionMappingPolicy.Decision.RETRY);

        assertSame(policy, result);
    }

    @Test
    void testWhenWithNullPredicate() {
        ExceptionMappingPolicy<String> policy = new ExceptionMappingPolicy<>();

        assertThrows(NullPointerException.class,
                () -> policy.when(null, ExceptionMappingPolicy.Decision.RETRY));
    }

    @Test
    void testWhenWithNullDecision() {
        ExceptionMappingPolicy<String> policy = new ExceptionMappingPolicy<>();
        Predicate<Throwable> predicate = e -> e instanceof RuntimeException;

        assertThrows(NullPointerException.class,
                () -> policy.when(predicate, null));
    }

    @Test
    void testOtherwise() {
        ExceptionMappingPolicy<String> policy = new ExceptionMappingPolicy<>();
        ExceptionMappingPolicy<String> result = policy.otherwise(ExceptionMappingPolicy.Decision.RETRY);

        assertSame(policy, result);
    }

    @Test
    void testOtherwiseWithNullDecision() {
        ExceptionMappingPolicy<String> policy = new ExceptionMappingPolicy<>();

        assertThrows(NullPointerException.class,
                () -> policy.otherwise(null));
    }

    @Test
    void testShouldRetryWithNullError() {
        ExceptionMappingPolicy<String> policy = new ExceptionMappingPolicy<>();

        assertFalse(policy.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testShouldRetryWhenAttemptExceedsMax() {
        ExceptionMappingPolicy<String> policy = new ExceptionMappingPolicy<>();
        RuntimeException error = new RuntimeException("error");

        assertFalse(policy.shouldRetry(null, error, 5, 5));
    }

    @Test
    void testShouldRetryWithMatchingRule() {
        ExceptionMappingPolicy<String> policy = new ExceptionMappingPolicy<>();
        policy.when(e -> e instanceof RuntimeException, ExceptionMappingPolicy.Decision.RETRY);
        
        RuntimeException error = new RuntimeException("error");
        assertTrue(policy.shouldRetry(null, error, 1, 5));
    }

    @Test
    void testShouldRetryWithNonMatchingRule() {
        ExceptionMappingPolicy<String> policy = new ExceptionMappingPolicy<>();
        policy.when(e -> e instanceof IllegalArgumentException, ExceptionMappingPolicy.Decision.RETRY);
        
        RuntimeException error = new RuntimeException("error");
        assertFalse(policy.shouldRetry(null, error, 1, 5));
    }

    @Test
    void testShouldRetryWithFailDecision() {
        ExceptionMappingPolicy<String> policy = new ExceptionMappingPolicy<>();
        policy.when(e -> e instanceof RuntimeException, ExceptionMappingPolicy.Decision.FAIL);
        
        RuntimeException error = new RuntimeException("error");
        assertFalse(policy.shouldRetry(null, error, 1, 5));
    }

    @Test
    void testShouldRetryWithIgnoreDecision() {
        ExceptionMappingPolicy<String> policy = new ExceptionMappingPolicy<>();
        policy.when(e -> e instanceof RuntimeException, ExceptionMappingPolicy.Decision.IGNORE);
        
        RuntimeException error = new RuntimeException("error");
        assertFalse(policy.shouldRetry(null, error, 1, 5));
    }

    @Test
    void testShouldRetryWithOtherwise() {
        ExceptionMappingPolicy<String> policy = new ExceptionMappingPolicy<>();
        policy.otherwise(ExceptionMappingPolicy.Decision.RETRY);
        
        RuntimeException error = new RuntimeException("error");
        assertTrue(policy.shouldRetry(null, error, 1, 5));
    }

    @Test
    void testShouldRetryWithMultipleRules() {
        ExceptionMappingPolicy<String> policy = new ExceptionMappingPolicy<>();
        policy.when(e -> e instanceof IllegalArgumentException, ExceptionMappingPolicy.Decision.RETRY);
        policy.when(e -> e instanceof IllegalStateException, ExceptionMappingPolicy.Decision.FAIL);
        policy.otherwise(ExceptionMappingPolicy.Decision.IGNORE);
        
        IllegalArgumentException error1 = new IllegalArgumentException("error1");
        IllegalStateException error2 = new IllegalStateException("error2");
        RuntimeException error3 = new RuntimeException("error3");
        
        assertTrue(policy.shouldRetry(null, error1, 1, 5));
        assertFalse(policy.shouldRetry(null, error2, 1, 5));
        assertFalse(policy.shouldRetry(null, error3, 1, 5));
    }

    @Test
    void testShouldRetryWithFirstMatchingRule() {
        ExceptionMappingPolicy<String> policy = new ExceptionMappingPolicy<>();
        policy.when(e -> e instanceof RuntimeException, ExceptionMappingPolicy.Decision.RETRY);
        policy.when(e -> e instanceof RuntimeException, ExceptionMappingPolicy.Decision.FAIL);
        
        RuntimeException error = new RuntimeException("error");
        assertTrue(policy.shouldRetry(null, error, 1, 5));
    }
}

