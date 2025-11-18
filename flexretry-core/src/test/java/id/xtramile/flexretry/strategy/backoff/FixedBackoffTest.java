package id.xtramile.flexretry.strategy.backoff;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class FixedBackoffTest {

    @Test
    void testConstructorWithValidDelay() {
        FixedBackoff backoff = new FixedBackoff(Duration.ofMillis(100));

        assertNotNull(backoff);
    }

    @Test
    void testConstructorWithZeroDelay() {
        FixedBackoff backoff = new FixedBackoff(Duration.ZERO);

        assertNotNull(backoff);
    }

    @Test
    void testConstructorWithNullDelay() {
        assertThrows(IllegalArgumentException.class,
                () -> new FixedBackoff(null));
    }

    @Test
    void testConstructorWithNegativeDelay() {
        assertThrows(IllegalArgumentException.class,
                () -> new FixedBackoff(Duration.ofMillis(-1)));
    }

    @Test
    void testDelayForAttempt() {
        FixedBackoff backoff = new FixedBackoff(Duration.ofMillis(100));
        
        assertEquals(Duration.ofMillis(100), backoff.delayForAttempt(1));
        assertEquals(Duration.ofMillis(100), backoff.delayForAttempt(2));
        assertEquals(Duration.ofMillis(100), backoff.delayForAttempt(10));
    }

    @Test
    void testOfMillis() {
        BackoffStrategy backoff = FixedBackoff.ofMillis(200);

        assertNotNull(backoff);
        assertEquals(Duration.ofMillis(200), backoff.delayForAttempt(1));
    }

    @Test
    void testOfMillisWithNegative() {
        BackoffStrategy backoff = FixedBackoff.ofMillis(-100);

        assertEquals(Duration.ZERO, backoff.delayForAttempt(1));
    }
}

