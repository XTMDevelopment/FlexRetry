package id.xtramile.flexretry.strategy.backoff;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class DecorrelatedJitterBackoffTest {

    @Test
    void testConstructorWithValidParameters() {
        DecorrelatedJitterBackoff backoff = new DecorrelatedJitterBackoff(
                Duration.ofMillis(100),
                Duration.ofMillis(1000)
        );

        assertNotNull(backoff);
    }

    @Test
    void testConstructorWithNullBase() {
        assertThrows(IllegalArgumentException.class,
                () -> new DecorrelatedJitterBackoff(null, Duration.ofMillis(1000)));
    }

    @Test
    void testConstructorWithNegativeBase() {
        assertThrows(IllegalArgumentException.class,
                () -> new DecorrelatedJitterBackoff(Duration.ofMillis(-1), Duration.ofMillis(1000)));
    }

    @Test
    void testConstructorWithNullCap() {
        assertThrows(IllegalArgumentException.class,
                () -> new DecorrelatedJitterBackoff(Duration.ofMillis(100), null));
    }

    @Test
    void testConstructorWithNegativeCap() {
        assertThrows(IllegalArgumentException.class,
                () -> new DecorrelatedJitterBackoff(Duration.ofMillis(100), Duration.ofMillis(-1)));
    }

    @Test
    void testDelayForAttempt() {
        DecorrelatedJitterBackoff backoff = new DecorrelatedJitterBackoff(
                Duration.ofMillis(100),
                Duration.ofMillis(1000)
        );

        Duration delay1 = backoff.delayForAttempt(1);
        assertTrue(delay1.toMillis() >= 100 && delay1.toMillis() <= 1000);

        Duration delay2 = backoff.delayForAttempt(2);
        assertTrue(delay2.toMillis() >= 100 && delay2.toMillis() <= 1000);
    }

    @Test
    void testDelayForAttemptWithState() {
        DecorrelatedJitterBackoff backoff = new DecorrelatedJitterBackoff(
                Duration.ofMillis(100),
                Duration.ofMillis(1000)
        );

        Duration delay1 = backoff.delayForAttempt(1);
        Duration delay2 = backoff.delayForAttempt(2);
        Duration delay3 = backoff.delayForAttempt(3);

        assertTrue(delay1.toMillis() >= 100 && delay1.toMillis() <= 1000);
        assertTrue(delay2.toMillis() >= 100 && delay2.toMillis() <= 1000);
        assertTrue(delay3.toMillis() >= 100 && delay3.toMillis() <= 1000);
    }

    @Test
    void testDelayForAttemptWithSmallCap() {
        DecorrelatedJitterBackoff backoff = new DecorrelatedJitterBackoff(
                Duration.ofMillis(100),
                Duration.ofMillis(200)
        );

        for (int i = 1; i <= 10; i++) {
            Duration delay = backoff.delayForAttempt(i);
            assertTrue(delay.toMillis() <= 200);
        }
    }
}

