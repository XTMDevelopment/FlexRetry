package id.xtramile.flexretry.config;

import id.xtramile.flexretry.RetryListeners;
import id.xtramile.flexretry.Sleeper;
import id.xtramile.flexretry.strategy.backoff.FixedBackoff;
import id.xtramile.flexretry.strategy.policy.RetryPolicy;
import id.xtramile.flexretry.strategy.stop.FixedAttemptsStop;
import id.xtramile.flexretry.support.time.Clock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class RetryTemplateTest {

    @Test
    void testConstructor() {
        RetryConfig<String> config = new RetryConfig<>(
                "test", "id", Map.of(),
                new FixedAttemptsStop(1),
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> false,
                new RetryListeners<>(),
                Sleeper.system(),
                Clock.system(),
                null, null,
                null, null, null, null
        );
        
        RetryTemplate<String> template = new RetryTemplate<>(config);
        assertNotNull(template);
    }

    @Test
    void testRun() {
        RetryPolicy<String> policy = (result, error, attempt, maxAttempts) -> false;
        RetryConfig<String> config = new RetryConfig<>(
                "test", "id", Map.of(),
                new FixedAttemptsStop(1),
                new FixedBackoff(Duration.ZERO),
                policy,
                new RetryListeners<>(),
                Sleeper.system(),
                Clock.system(),
                null, null,
                null,
                null, null, null
        );
        
        RetryTemplate<String> template = new RetryTemplate<>(config);
        String result = template.run(() -> "success");
        assertEquals("success", result);
    }

    @Test
    void testRunAsync() throws Exception {
        Executor executor = Executors.newSingleThreadExecutor();
        RetryPolicy<String> policy = (result, error, attempt, maxAttempts) -> false;
        RetryConfig<String> config = new RetryConfig<>(
                "test", "id", Map.of(),
                new FixedAttemptsStop(1),
                new FixedBackoff(Duration.ZERO),
                policy,
                new RetryListeners<>(),
                Sleeper.system(),
                Clock.system(),
                null, null,
                null,
                null, null, null
        );
        
        RetryTemplate<String> template = new RetryTemplate<>(config);
        CompletableFuture<String> future = template.runAsync(() -> "success", executor);
        String result = future.get();
        assertEquals("success", result);
    }
}

