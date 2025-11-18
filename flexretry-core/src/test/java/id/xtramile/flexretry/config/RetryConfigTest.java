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

class RetryConfigTest {

    @Test
    void testConstructor() {
        RetryConfig<String> config = new RetryConfig<>(
                "test", "id", Map.of(),
                new FixedAttemptsStop(3),
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> false,
                new RetryListeners<>(),
                Sleeper.system(),
                Clock.system(),
                null, null,
                null, null, null, null
        );

        assertNotNull(config);
        assertEquals("test", config.name);
        assertEquals("id", config.id);
    }

    @Test
    void testConstructorWithNullName() {
        assertThrows(NullPointerException.class, () -> {
            new RetryConfig<>(
                    null, "id", Map.of(),
                    new FixedAttemptsStop(3),
                    new FixedBackoff(Duration.ZERO),
                    (result, error, attempt, maxAttempts) -> false,
                    new RetryListeners<>(),
                    Sleeper.system(),
                    Clock.system(),
                    null, null,
                    null, null, null, null
            );
        });
    }

    @Test
    void testConstructorWithNullId() {
        assertThrows(NullPointerException.class, () -> {
            new RetryConfig<>(
                    "test", null, Map.of(),
                    new FixedAttemptsStop(3),
                    new FixedBackoff(Duration.ZERO),
                    (result, error, attempt, maxAttempts) -> false,
                    new RetryListeners<>(),
                    Sleeper.system(),
                    Clock.system(),
                    null, null,
                    null, null, null, null
            );
        });
    }

    @Test
    void testConstructorWithNullTags() {
        // RetryConfig requires non-null tags, so passing null should throw NPE
        assertThrows(NullPointerException.class, () -> {
            new RetryConfig<>(
                    "test", "id", null,
                    new FixedAttemptsStop(3),
                    new FixedBackoff(Duration.ZERO),
                    (result, error, attempt, maxAttempts) -> false,
                    new RetryListeners<>(),
                    Sleeper.system(),
                    Clock.system(),
                    null, null,
                    null, null, null, null
            );
        });
    }

    @Test
    void testRun() {
        RetryPolicy<String> policy = (result, error, attempt, maxAttempts) -> false;
        RetryConfig<String> config = new RetryConfig<String>(
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
        
        String result = config.run(() -> "success");
        assertEquals("success", result);
    }

    @Test
    void testRunAsync() throws Exception {
        Executor executor = Executors.newSingleThreadExecutor();
        RetryPolicy<String> policy = (result, error, attempt, maxAttempts) -> false;
        RetryConfig<String> config = new RetryConfig<String>(
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
        
        CompletableFuture<String> future = config.runAsync(() -> "success", executor);
        String result = future.get();
        assertEquals("success", result);
    }

    @Test
    void testRunAsyncWithNullExecutor() {
        RetryPolicy<String> policy = (result, error, attempt, maxAttempts) -> false;
        RetryConfig<String> config = new RetryConfig<String>(
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
        
        assertThrows(NullPointerException.class, () -> {
            config.runAsync(() -> "success", null);
        });
    }

    @Test
    void testConstructorWithDefaultListeners() {
        RetryConfig<String> config = new RetryConfig<>(
                "test", "id", Map.of(),
                new FixedAttemptsStop(3),
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> false,
                null,
                null,
                null,
                null, null,
                null, null, null, null
        );
        assertNotNull(config.listeners);
        assertNotNull(config.sleeper);
        assertNotNull(config.clock);
    }
}

