package id.xtramile.flexretry.strategy.timeout;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class FixedTimeoutTest {

    @Test
    void testConstructorWithValidDuration() {
        FixedTimeout timeout = new FixedTimeout(Duration.ofMillis(100));
        assertNotNull(timeout);
    }

    @Test
    void testConstructorWithZeroDuration() {
        FixedTimeout timeout = new FixedTimeout(Duration.ZERO);
        assertNotNull(timeout);
    }

    @Test
    void testConstructorWithNullDuration() {
        assertThrows(IllegalArgumentException.class,
                () -> new FixedTimeout(null));
    }

    @Test
    void testConstructorWithNegativeDuration() {
        assertThrows(IllegalArgumentException.class,
                () -> new FixedTimeout(Duration.ofMillis(-1)));
    }

    @Test
    void testTimeoutForAttempt() {
        FixedTimeout timeout = new FixedTimeout(Duration.ofMillis(100));
        
        assertEquals(Duration.ofMillis(100), timeout.timeoutForAttempt(1));
        assertEquals(Duration.ofMillis(100), timeout.timeoutForAttempt(2));
        assertEquals(Duration.ofMillis(100), timeout.timeoutForAttempt(10));
    }

    @Test
    void testOfMillis() {
        AttemptTimeoutStrategy timeout = FixedTimeout.ofMillis(200);
        assertNotNull(timeout);
        assertEquals(Duration.ofMillis(200), timeout.timeoutForAttempt(1));
    }

    @Test
    void testOfMillisWithNegative() {
        AttemptTimeoutStrategy timeout = FixedTimeout.ofMillis(-100);
        assertEquals(Duration.ZERO, timeout.timeoutForAttempt(1));
    }
}

