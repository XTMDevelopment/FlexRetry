package id.xtramile.flexretry.strategy.backoff;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class BackoffStrategyTest {

    @Test
    void testFixed() {
        BackoffStrategy strategy = BackoffStrategy.fixed(Duration.ofMillis(100));

        assertNotNull(strategy);
        assertInstanceOf(FixedBackoff.class, strategy);
        assertEquals(Duration.ofMillis(100), strategy.delayForAttempt(1));
    }

    @Test
    void testExponential() {
        BackoffStrategy strategy = BackoffStrategy.exponential(Duration.ofMillis(100), 2.0);

        assertNotNull(strategy);
        assertInstanceOf(ExponentialBackoff.class, strategy);
        assertEquals(Duration.ofMillis(100), strategy.delayForAttempt(1));
        assertEquals(Duration.ofMillis(200), strategy.delayForAttempt(2));
    }

    @Test
    void testWithJitter() {
        BackoffStrategy base = BackoffStrategy.fixed(Duration.ofMillis(100));
        BackoffStrategy withJitter = base.withJitter(0.1);
        
        assertNotNull(withJitter);

        for (int i = 0; i < 10; i++) {
            Duration delay = withJitter.delayForAttempt(1);
            assertTrue(delay.toMillis() >= 90 && delay.toMillis() <= 110);
        }
    }

    @Test
    void testWithJitterInvalidFraction() {
        BackoffStrategy base = BackoffStrategy.fixed(Duration.ofMillis(100));
        
        assertThrows(IllegalArgumentException.class, () -> base.withJitter(-0.1));
        assertThrows(IllegalArgumentException.class, () -> base.withJitter(1.1));
    }

    @Test
    void testWithJitterZeroFraction() {
        BackoffStrategy base = BackoffStrategy.fixed(Duration.ofMillis(100));
        BackoffStrategy withJitter = base.withJitter(0.0);

        assertEquals(Duration.ofMillis(100), withJitter.delayForAttempt(1));
    }

    @Test
    void testWithJitterFullFraction() {
        BackoffStrategy base = BackoffStrategy.fixed(Duration.ofMillis(100));
        BackoffStrategy withJitter = base.withJitter(1.0);

        for (int i = 0; i < 10; i++) {
            Duration delay = withJitter.delayForAttempt(1);
            assertTrue(delay.toMillis() >= 0 && delay.toMillis() <= 200);
        }
    }

    @Test
    void testWithJitterWithZeroBaseDelay() {
        BackoffStrategy base = BackoffStrategy.fixed(Duration.ZERO);
        BackoffStrategy withJitter = base.withJitter(0.5);

        Duration delay = withJitter.delayForAttempt(1);
        assertNotNull(delay);
        assertTrue(delay.toMillis() >= 0);
    }
}

