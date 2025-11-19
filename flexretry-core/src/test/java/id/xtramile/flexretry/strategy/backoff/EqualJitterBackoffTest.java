package id.xtramile.flexretry.strategy.backoff;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class EqualJitterBackoffTest {

    @Test
    void testConstructorWithValidParameters() {
        EqualJitterBackoff backoff = new EqualJitterBackoff(Duration.ofMillis(100), 2.0);
        assertNotNull(backoff);
    }

    @Test
    void testConstructorWithNullBase() {
        assertThrows(IllegalArgumentException.class,
                () -> new EqualJitterBackoff(null, 2.0));
    }

    @Test
    void testConstructorWithNegativeBase() {
        assertThrows(IllegalArgumentException.class,
                () -> new EqualJitterBackoff(Duration.ofMillis(-1), 2.0));
    }

    @Test
    void testConstructorWithInvalidMultiplier() {
        assertThrows(IllegalArgumentException.class,
                () -> new EqualJitterBackoff(Duration.ofMillis(100), 0.5));
    }

    @Test
    void testDelayForAttempt() {
        EqualJitterBackoff backoff = new EqualJitterBackoff(Duration.ofMillis(100), 2.0);

        for (int i = 0; i < 10; i++) {
            Duration delay = backoff.delayForAttempt(1);
            assertTrue(delay.toMillis() >= 50 && delay.toMillis() <= 100);
        }
    }

    @Test
    void testDelayForAttemptWithDifferentAttempts() {
        EqualJitterBackoff backoff = new EqualJitterBackoff(Duration.ofMillis(100), 2.0);

        Duration delay1 = backoff.delayForAttempt(1);
        assertTrue(delay1.toMillis() >= 50 && delay1.toMillis() <= 100);

        Duration delay2 = backoff.delayForAttempt(2);
        assertTrue(delay2.toMillis() >= 100 && delay2.toMillis() <= 200);
    }

    @Test
    void testDelayForAttemptWithMultiplierOne() {
        EqualJitterBackoff backoff = new EqualJitterBackoff(Duration.ofMillis(100), 1.0);

        for (int i = 1; i <= 5; i++) {
            Duration delay = backoff.delayForAttempt(i);
            assertTrue(delay.toMillis() >= 0 && delay.toMillis() <= 100);
        }
    }
}

