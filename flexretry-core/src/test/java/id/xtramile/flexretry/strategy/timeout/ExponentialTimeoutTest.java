package id.xtramile.flexretry.strategy.timeout;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ExponentialTimeoutTest {

    @Test
    void testConstructorWithValidParameters() {
        ExponentialTimeout timeout = new ExponentialTimeout(
                Duration.ofMillis(100),
                2.0,
                Duration.ofMillis(1000)
        );

        assertNotNull(timeout);
    }

    @Test
    void testConstructorWithNullInitial() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExponentialTimeout(null, 2.0, Duration.ofMillis(1000)));
    }

    @Test
    void testConstructorWithNegativeInitial() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExponentialTimeout(Duration.ofMillis(-1), 2.0, Duration.ofMillis(1000)));
    }

    @Test
    void testConstructorWithInvalidMultiplier() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExponentialTimeout(Duration.ofMillis(100), 0.5, Duration.ofMillis(1000)));
    }

    @Test
    void testConstructorWithNullCap() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExponentialTimeout(Duration.ofMillis(100), 2.0, null));
    }

    @Test
    void testConstructorWithNegativeCap() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExponentialTimeout(Duration.ofMillis(100), 2.0, Duration.ofMillis(-1)));
    }

    @Test
    void testTimeoutForAttempt() {
        ExponentialTimeout timeout = new ExponentialTimeout(
                Duration.ofMillis(100),
                2.0,
                Duration.ofMillis(1000)
        );

        assertEquals(Duration.ofMillis(100), timeout.timeoutForAttempt(1));
        assertEquals(Duration.ofMillis(200), timeout.timeoutForAttempt(2));
        assertEquals(Duration.ofMillis(400), timeout.timeoutForAttempt(3));
    }

    @Test
    void testTimeoutForAttemptWithCap() {
        ExponentialTimeout timeout = new ExponentialTimeout(
                Duration.ofMillis(100),
                2.0,
                Duration.ofMillis(300)
        );

        assertEquals(Duration.ofMillis(100), timeout.timeoutForAttempt(1));
        assertEquals(Duration.ofMillis(200), timeout.timeoutForAttempt(2));
        assertEquals(Duration.ofMillis(300), timeout.timeoutForAttempt(3));
        assertEquals(Duration.ofMillis(300), timeout.timeoutForAttempt(4));
    }

    @Test
    void testTimeoutForAttemptWithMultiplierOne() {
        ExponentialTimeout timeout = new ExponentialTimeout(
                Duration.ofMillis(100),
                1.0,
                Duration.ofMillis(1000)
        );

        assertEquals(Duration.ofMillis(100), timeout.timeoutForAttempt(1));
        assertEquals(Duration.ofMillis(100), timeout.timeoutForAttempt(2));
        assertEquals(Duration.ofMillis(100), timeout.timeoutForAttempt(10));
    }

    @Test
    void testOfMillis() {
        AttemptTimeoutStrategy timeout = ExponentialTimeout.ofMillis(100, 2.0, 1000);

        assertNotNull(timeout);
        assertEquals(Duration.ofMillis(100), timeout.timeoutForAttempt(1));
        assertEquals(Duration.ofMillis(200), timeout.timeoutForAttempt(2));
    }

    @Test
    void testTimeoutForAttemptWithOverflow() {
        ExponentialTimeout timeout = new ExponentialTimeout(
                Duration.ofMillis(Long.MAX_VALUE / 2),
                2.0,
                Duration.ofMillis(Long.MAX_VALUE)
        );

        Duration timeoutDuration = timeout.timeoutForAttempt(2);

        assertNotNull(timeoutDuration);
        assertTrue(timeoutDuration.toMillis() >= 0);
    }
}

