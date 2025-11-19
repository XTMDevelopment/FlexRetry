package id.xtramile.flexretry.strategy.stop;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StopStrategyTest {

    @Test
    void testMaxAttempts() {
        StopStrategy strategy = StopStrategy.maxAttempts(5);

        assertNotNull(strategy);
        assertInstanceOf(FixedAttemptsStop.class, strategy);

        Optional<Integer> maxAttempts = strategy.maxAttempts();

        assertTrue(maxAttempts.isPresent());
        assertEquals(5, maxAttempts.get());
    }

    @Test
    void testMaxElapsed() {
        StopStrategy strategy = StopStrategy.maxElapsed(Duration.ofSeconds(10));

        assertNotNull(strategy);
        assertInstanceOf(MaxElapsedStop.class, strategy);
    }

    @Test
    void testCompose() {
        StopStrategy strategy1 = StopStrategy.maxAttempts(3);
        StopStrategy strategy2 = StopStrategy.maxAttempts(5);
        StopStrategy composite = StopStrategy.compose(strategy1, strategy2);

        assertNotNull(composite);
        assertInstanceOf(CompositeStop.class, composite);
    }

    @Test
    void testDefaultMaxAttempts() {
        StopStrategy strategy = (attempt, startNanos, nowNanos, nextDelay) -> false;

        Optional<Integer> maxAttempts = strategy.maxAttempts();
        assertFalse(maxAttempts.isPresent());
    }
}

