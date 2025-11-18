package id.xtramile.flexretry.strategy.timeout;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class AttemptTimeoutStrategyTest {

    @Test
    void testFixed() {
        AttemptTimeoutStrategy strategy = AttemptTimeoutStrategy.fixed(Duration.ofMillis(100));

        assertNotNull(strategy);
        assertInstanceOf(FixedTimeout.class, strategy);
        assertEquals(Duration.ofMillis(100), strategy.timeoutForAttempt(1));
    }

    @Test
    void testExponential() {
        AttemptTimeoutStrategy strategy = AttemptTimeoutStrategy.exponential(
                Duration.ofMillis(100),
                2.0,
                Duration.ofMillis(1000)
        );

        assertNotNull(strategy);
        assertInstanceOf(ExponentialTimeout.class, strategy);
        assertEquals(Duration.ofMillis(100), strategy.timeoutForAttempt(1));
        assertEquals(Duration.ofMillis(200), strategy.timeoutForAttempt(2));
    }
}

