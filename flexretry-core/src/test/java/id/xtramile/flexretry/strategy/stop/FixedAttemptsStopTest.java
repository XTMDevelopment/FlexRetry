package id.xtramile.flexretry.strategy.stop;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FixedAttemptsStopTest {

    @Test
    void testConstructorWithValidMaxAttempts() {
        FixedAttemptsStop stop = new FixedAttemptsStop(5);
        assertNotNull(stop);
    }

    @Test
    void testConstructorWithInvalidMaxAttempts() {
        assertThrows(IllegalArgumentException.class,
                () -> new FixedAttemptsStop(0));

        assertThrows(IllegalArgumentException.class,
                () -> new FixedAttemptsStop(-1));
    }

    @Test
    void testShouldStop() {
        FixedAttemptsStop stop = new FixedAttemptsStop(3);

        assertFalse(stop.shouldStop(1, 0, 1000, Duration.ZERO));
        assertFalse(stop.shouldStop(2, 0, 1000, Duration.ZERO));
        assertFalse(stop.shouldStop(3, 0, 1000, Duration.ZERO));
        assertTrue(stop.shouldStop(4, 0, 1000, Duration.ZERO));
        assertTrue(stop.shouldStop(5, 0, 1000, Duration.ZERO));
    }

    @Test
    void testMaxAttempts() {
        FixedAttemptsStop stop = new FixedAttemptsStop(5);
        Optional<Integer> maxAttempts = stop.maxAttempts();

        assertTrue(maxAttempts.isPresent());
        assertEquals(5, maxAttempts.get());
    }

    @Test
    void testShouldStopIgnoresTimeParameters() {
        FixedAttemptsStop stop = new FixedAttemptsStop(2);

        assertFalse(stop.shouldStop(1, 0, 1000000, Duration.ofSeconds(10)));
        assertTrue(stop.shouldStop(3, 0, 1000000, Duration.ofSeconds(10)));
    }
}

