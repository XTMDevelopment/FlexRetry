package id.xtramile.flexretry.strategy.stop;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CompositeStopTest {

    @Test
    void testConstructorWithSingleStrategy() {
        FixedAttemptsStop stop1 = new FixedAttemptsStop(3);
        CompositeStop composite = new CompositeStop(stop1);

        assertNotNull(composite);
    }

    @Test
    void testConstructorWithMultipleStrategies() {
        FixedAttemptsStop stop1 = new FixedAttemptsStop(3);
        FixedAttemptsStop stop2 = new FixedAttemptsStop(5);
        CompositeStop composite = new CompositeStop(stop1, stop2);

        assertNotNull(composite);
    }

    @Test
    void testShouldStopWhenAnyStrategyReturnsTrue() {
        FixedAttemptsStop stop1 = new FixedAttemptsStop(3);
        FixedAttemptsStop stop2 = new FixedAttemptsStop(5);
        CompositeStop composite = new CompositeStop(stop1, stop2);

        assertTrue(composite.shouldStop(4, 0, 1000, Duration.ZERO));
    }

    @Test
    void testShouldStopWhenAllStrategiesReturnFalse() {
        FixedAttemptsStop stop1 = new FixedAttemptsStop(3);
        FixedAttemptsStop stop2 = new FixedAttemptsStop(5);
        CompositeStop composite = new CompositeStop(stop1, stop2);

        assertFalse(composite.shouldStop(2, 0, 1000, Duration.ZERO));
    }

    @Test
    void testShouldStopWithNullStrategy() {
        FixedAttemptsStop stop1 = new FixedAttemptsStop(3);
        CompositeStop composite = new CompositeStop(stop1, null);

        assertTrue(composite.shouldStop(4, 0, 1000, Duration.ZERO));
    }

    @Test
    void testMaxAttemptsWithSingleStrategy() {
        FixedAttemptsStop stop1 = new FixedAttemptsStop(3);
        CompositeStop composite = new CompositeStop(stop1);

        Optional<Integer> maxAttempts = composite.maxAttempts();
        assertTrue(maxAttempts.isPresent());
        assertEquals(3, maxAttempts.get());
    }

    @Test
    void testMaxAttemptsWithMultipleStrategies() {
        FixedAttemptsStop stop1 = new FixedAttemptsStop(3);
        FixedAttemptsStop stop2 = new FixedAttemptsStop(5);
        CompositeStop composite = new CompositeStop(stop1, stop2);

        Optional<Integer> maxAttempts = composite.maxAttempts();
        assertTrue(maxAttempts.isPresent());
        assertEquals(3, maxAttempts.get());
    }

    @Test
    void testMaxAttemptsWithNoFixedLimit() {
        MaxElapsedStop stop1 = new MaxElapsedStop(Duration.ofSeconds(10));
        CompositeStop composite = new CompositeStop(stop1);

        Optional<Integer> maxAttempts = composite.maxAttempts();
        assertFalse(maxAttempts.isPresent());
    }

    @Test
    void testMaxAttemptsWithMixedStrategies() {
        FixedAttemptsStop stop1 = new FixedAttemptsStop(3);
        MaxElapsedStop stop2 = new MaxElapsedStop(Duration.ofSeconds(10));
        CompositeStop composite = new CompositeStop(stop1, stop2);

        Optional<Integer> maxAttempts = composite.maxAttempts();
        assertTrue(maxAttempts.isPresent());
        assertEquals(3, maxAttempts.get());
    }

    @Test
    void testMaxAttemptsWithNullStrategies() {
        FixedAttemptsStop stop1 = new FixedAttemptsStop(3);
        CompositeStop composite = new CompositeStop(stop1, null, null);

        Optional<Integer> maxAttempts = composite.maxAttempts();
        assertTrue(maxAttempts.isPresent());
        assertEquals(3, maxAttempts.get());
    }

    @Test
    void testShouldStopWithEmptyArray() {
        CompositeStop composite = new CompositeStop();
        assertFalse(composite.shouldStop(1, 0, 1000, Duration.ZERO));
    }
}

