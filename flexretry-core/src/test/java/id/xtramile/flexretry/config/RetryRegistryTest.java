package id.xtramile.flexretry.config;

import id.xtramile.flexretry.RetryListeners;
import id.xtramile.flexretry.Sleeper;
import id.xtramile.flexretry.strategy.backoff.FixedBackoff;
import id.xtramile.flexretry.strategy.stop.FixedAttemptsStop;
import id.xtramile.flexretry.support.time.Clock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RetryRegistryTest {

    @Test
    void testConstructor() {
        RetryRegistry registry = new RetryRegistry();
        assertNotNull(registry);
    }

    @Test
    void testRegister() {
        RetryRegistry registry = new RetryRegistry();
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
        
        registry.register("test-config", config);
        
        RetryTemplate<String> template = registry.template("test-config");
        assertNotNull(template);
    }

    @Test
    void testTemplate() {
        RetryRegistry registry = new RetryRegistry();
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
        
        registry.register("test-config", config);
        RetryTemplate<String> template = registry.template("test-config");
        assertNotNull(template);
    }

    @Test
    void testTemplateWithNonExistentName() {
        RetryRegistry registry = new RetryRegistry();
        
        assertThrows(IllegalArgumentException.class, () -> registry.template("non-existent"));
    }

    @Test
    void testRegisterMultipleConfigs() {
        RetryRegistry registry = new RetryRegistry();
        
        RetryConfig<String> config1 = new RetryConfig<>(
                "test1", "id1", Map.of(),
                new FixedAttemptsStop(1),
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> false,
                new RetryListeners<>(),
                Sleeper.system(),
                Clock.system(),
                null, null,
                null, null, null, null
        );
        
        RetryConfig<Integer> config2 = new RetryConfig<>(
                "test2", "id2", Map.of(),
                new FixedAttemptsStop(2),
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> false,
                new RetryListeners<>(),
                Sleeper.system(),
                Clock.system(),
                null, null,
                null, null, null, null
        );
        
        registry.register("config1", config1);
        registry.register("config2", config2);
        
        RetryTemplate<String> template1 = registry.template("config1");
        RetryTemplate<Integer> template2 = registry.template("config2");
        
        assertNotNull(template1);
        assertNotNull(template2);
    }

    @Test
    void testRegisterOverwritesExisting() {
        RetryRegistry registry = new RetryRegistry();
        
        RetryConfig<String> config1 = new RetryConfig<>(
                "test1", "id1", Map.of(),
                new FixedAttemptsStop(1),
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> false,
                new RetryListeners<>(),
                Sleeper.system(),
                Clock.system(),
                null, null,
                null, null, null, null
        );
        
        RetryConfig<String> config2 = new RetryConfig<>(
                "test2", "id2", Map.of(),
                new FixedAttemptsStop(2),
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> false,
                new RetryListeners<>(),
                Sleeper.system(),
                Clock.system(),
                null, null,
                null, null, null, null
        );
        
        registry.register("config", config1);
        registry.register("config", config2);
        
        RetryTemplate<String> template = registry.template("config");
        assertNotNull(template);
    }
}

