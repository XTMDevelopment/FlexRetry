package id.xtramile.flexretry.lifecycle;

import id.xtramile.flexretry.RetryContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("EmptyMethod")
class AttemptLifecycleTest {

    @Test
    void testBeforeAttempt() {
        AttemptLifecycle<String> lifecycle = new AttemptLifecycle<>() {
            @Override
            public void beforeAttempt(RetryContext<String> ctx) {
                // Default implementation does nothing
            }
        };
        
        RetryContext<String> ctx = new RetryContext<>(
                "id", "name", 1, 3, null, null, Duration.ZERO, Map.of()
        );

        assertDoesNotThrow(() -> lifecycle.beforeAttempt(ctx));
    }

    @Test
    void testAfterSuccess() {
        AttemptLifecycle<String> lifecycle = new AttemptLifecycle<>() {
            @Override
            public void afterSuccess(RetryContext<String> ctx) {
                // Default implementation does nothing
            }
        };
        
        RetryContext<String> ctx = new RetryContext<>(
                "id", "name", 1, 3, "result", null, Duration.ZERO, Map.of()
        );

        assertDoesNotThrow(() -> lifecycle.afterSuccess(ctx));
    }

    @Test
    void testAfterFailure() {
        AttemptLifecycle<String> lifecycle = new AttemptLifecycle<>() {
            @Override
            public void afterFailure(RetryContext<String> ctx, Throwable error) {
                // Default implementation does nothing
            }
        };
        
        RetryContext<String> ctx = new RetryContext<>(
                "id", "name", 1, 3, null, new RuntimeException("error"), Duration.ZERO, Map.of()
        );

        assertDoesNotThrow(() -> lifecycle.afterFailure(ctx, new RuntimeException("error")));
    }

    @Test
    void testCustomLifecycle() {
        AttemptLifecycle<String> lifecycle = new AttemptLifecycle<>() {
            @Override
            public void beforeAttempt(RetryContext<String> ctx) {
                // Custom implementation
            }

            @Override
            public void afterSuccess(RetryContext<String> ctx) {
                // Custom implementation
            }

            @Override
            public void afterFailure(RetryContext<String> ctx, Throwable error) {
                // Custom implementation
            }
        };
        
        RetryContext<String> ctx = new RetryContext<>(
                "id", "name", 1, 3, null, null, Duration.ZERO, Map.of()
        );
        
        assertDoesNotThrow(() -> lifecycle.beforeAttempt(ctx));
        assertDoesNotThrow(() -> lifecycle.afterSuccess(ctx));
        assertDoesNotThrow(() -> lifecycle.afterFailure(ctx, new RuntimeException("error")));
    }
}

