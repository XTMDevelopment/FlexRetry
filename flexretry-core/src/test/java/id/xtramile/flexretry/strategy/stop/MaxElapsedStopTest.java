package id.xtramile.flexretry.strategy.stop;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class MaxElapsedStopTest {

    @Test
    void testConstructorWithValidBudget() {
        MaxElapsedStop stop = new MaxElapsedStop(Duration.ofSeconds(10));
        assertNotNull(stop);
    }

    @Test
    void testConstructorWithZeroBudget() {
        MaxElapsedStop stop = new MaxElapsedStop(Duration.ZERO);
        assertNotNull(stop);
    }

    @Test
    void testConstructorWithNullBudget() {
        assertThrows(IllegalArgumentException.class,
                () -> new MaxElapsedStop(null));
    }

    @Test
    void testConstructorWithNegativeBudget() {
        assertThrows(IllegalArgumentException.class,
                () -> new MaxElapsedStop(Duration.ofSeconds(-1)));
    }

    @Test
    void testShouldStop() {
        MaxElapsedStop stop = new MaxElapsedStop(Duration.ofSeconds(10));
        long startNanos = 0;
        long nowNanos = Duration.ofSeconds(5).toNanos();
        Duration nextDelay = Duration.ofSeconds(4);

        assertFalse(stop.shouldStop(2, startNanos, nowNanos, nextDelay));

        nextDelay = Duration.ofSeconds(6);
        assertTrue(stop.shouldStop(2, startNanos, nowNanos, nextDelay));
    }

    @Test
    void testShouldStopWithExactBudget() {
        MaxElapsedStop stop = new MaxElapsedStop(Duration.ofSeconds(10));
        long startNanos = 0;
        long nowNanos = Duration.ofSeconds(5).toNanos();
        Duration nextDelay = Duration.ofSeconds(6);

        assertTrue(stop.shouldStop(2, startNanos, nowNanos, nextDelay));
    }

    @Test
    void testShouldStopWithNullNextDelay() {
        MaxElapsedStop stop = new MaxElapsedStop(Duration.ofSeconds(10));
        long startNanos = 0;
        long nowNanos = Duration.ofSeconds(5).toNanos();

        assertFalse(stop.shouldStop(2, startNanos, nowNanos, null));
    }

    @Test
    void testShouldStopWithNegativeNextDelay() {
        MaxElapsedStop stop = new MaxElapsedStop(Duration.ofSeconds(10));
        long startNanos = 0;
        long nowNanos = Duration.ofSeconds(5).toNanos();
        Duration nextDelay = Duration.ofSeconds(-1);

        assertFalse(stop.shouldStop(2, startNanos, nowNanos, nextDelay));
    }

    @Test
    void testBudget() {
        Duration budget = Duration.ofSeconds(10);
        MaxElapsedStop stop = new MaxElapsedStop(budget);

        assertEquals(budget, stop.budget());
    }

    @Test
    void testShouldStopIgnoresAttemptNumber() {
        MaxElapsedStop stop = new MaxElapsedStop(Duration.ofSeconds(10));
        long startNanos = 0;
        long nowNanos = Duration.ofSeconds(5).toNanos();
        Duration nextDelay = Duration.ofSeconds(6);

        assertTrue(stop.shouldStop(1, startNanos, nowNanos, nextDelay));
        assertTrue(stop.shouldStop(100, startNanos, nowNanos, nextDelay));
    }
}

