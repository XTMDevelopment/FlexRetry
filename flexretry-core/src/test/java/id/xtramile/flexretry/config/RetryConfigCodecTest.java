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

class RetryConfigCodecTest {

    @Test
    void testToMap() {
        RetryConfig<String> config = new RetryConfig<>(
                "test", "id", Map.of("key", "value"),
                new FixedAttemptsStop(1),
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> false,
                new RetryListeners<>(),
                Sleeper.system(),
                Clock.system(),
                null, null,
                null, null, null, null
        );
        
        Map<String, Object> map = RetryConfigCodec.toMap(config);
        
        assertNotNull(map);
        assertEquals("test", map.get("name"));
        assertEquals("id", map.get("id"));
        assertNotNull(map.get("tags"));
    }

    @Test
    void testFromMap() {
        Map<String, Object> map = Map.of("name", "test", "id", "id", "tags", Map.of());
        
        assertThrows(UnsupportedOperationException.class,
                () -> RetryConfigCodec.fromMap(map));
    }
}

