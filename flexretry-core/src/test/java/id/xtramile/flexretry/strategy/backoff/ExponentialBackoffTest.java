package id.xtramile.flexretry.strategy.backoff;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ExponentialBackoffTest {

    @Test
    void testConstructorWithValidParameters() {
        ExponentialBackoff backoff = new ExponentialBackoff(Duration.ofMillis(100), 2.0);
        assertNotNull(backoff);
    }

    @Test
    void testConstructorWithNullInitial() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExponentialBackoff(null, 2.0));
    }

    @Test
    void testConstructorWithNegativeInitial() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExponentialBackoff(Duration.ofMillis(-1), 2.0));
    }

    @Test
    void testConstructorWithInvalidMultiplier() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExponentialBackoff(Duration.ofMillis(100), 0.5));
    }

    @Test
    void testDelayForAttempt() {
        ExponentialBackoff backoff = new ExponentialBackoff(Duration.ofMillis(100), 2.0);

        assertEquals(Duration.ofMillis(100), backoff.delayForAttempt(1));
        assertEquals(Duration.ofMillis(200), backoff.delayForAttempt(2));
        assertEquals(Duration.ofMillis(400), backoff.delayForAttempt(3));
    }

    @Test
    void testDelayForAttemptWithMultiplierOne() {
        ExponentialBackoff backoff = new ExponentialBackoff(Duration.ofMillis(100), 1.0);

        assertEquals(Duration.ofMillis(100), backoff.delayForAttempt(1));
        assertEquals(Duration.ofMillis(100), backoff.delayForAttempt(2));
        assertEquals(Duration.ofMillis(100), backoff.delayForAttempt(10));
    }

    @Test
    void testDelayForAttemptWithLargeMultiplier() {
        ExponentialBackoff backoff = new ExponentialBackoff(Duration.ofMillis(100), 3.0);
        
        assertEquals(Duration.ofMillis(100), backoff.delayForAttempt(1));
        assertEquals(Duration.ofMillis(300), backoff.delayForAttempt(2));
        assertEquals(Duration.ofMillis(900), backoff.delayForAttempt(3));
    }

    @Test
    void testOfMillis() {
        BackoffStrategy backoff = ExponentialBackoff.ofMillis(100, 2.0);

        assertNotNull(backoff);
        assertEquals(Duration.ofMillis(100), backoff.delayForAttempt(1));
        assertEquals(Duration.ofMillis(200), backoff.delayForAttempt(2));
    }

    @Test
    void testDelayForAttemptWithOverflow() {
        ExponentialBackoff backoff = new ExponentialBackoff(Duration.ofMillis(Long.MAX_VALUE / 2), 2.0);

        Duration delay = backoff.delayForAttempt(2);
        assertNotNull(delay);
        assertTrue(delay.toMillis() >= 0);
    }
}

