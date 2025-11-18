package id.xtramile.flexretry.support.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ManualClockTest {

    @Test
    void testConstructorWithNoArgs() {
        ManualClock clock = new ManualClock();
        assertEquals(0L, clock.nanoTime());
    }

    @Test
    void testConstructorWithInitialNanos() {
        ManualClock clock = new ManualClock(1000L);
        assertEquals(1000L, clock.nanoTime());
    }

    @Test
    void testNanoTime() {
        ManualClock clock = new ManualClock(5000L);
        assertEquals(5000L, clock.nanoTime());
    }

    @Test
    void testAdvanceNanos() {
        ManualClock clock = new ManualClock(1000L);
        
        clock.advanceNanos(500L);
        assertEquals(1500L, clock.nanoTime());
        
        clock.advanceNanos(200L);
        assertEquals(1700L, clock.nanoTime());
    }

    @Test
    void testAdvanceNanosReturnsThis() {
        ManualClock clock = new ManualClock(1000L);
        ManualClock result = clock.advanceNanos(500L);
        assertSame(clock, result);
    }

    @Test
    void testAdvanceMillis() {
        ManualClock clock = new ManualClock(0L);
        
        clock.advanceMillis(1);
        assertEquals(1_000_000L, clock.nanoTime());
        
        clock.advanceMillis(2);
        assertEquals(3_000_000L, clock.nanoTime());
    }

    @Test
    void testAdvanceMillisReturnsThis() {
        ManualClock clock = new ManualClock(0L);
        ManualClock result = clock.advanceMillis(1);
        assertSame(clock, result);
    }

    @Test
    void testSetNanos() {
        ManualClock clock = new ManualClock(1000L);
        
        clock.setNanos(5000L);
        assertEquals(5000L, clock.nanoTime());
        
        clock.setNanos(2000L);
        assertEquals(2000L, clock.nanoTime());
    }

    @Test
    void testSetNanosReturnsThis() {
        ManualClock clock = new ManualClock(1000L);
        ManualClock result = clock.setNanos(5000L);
        assertSame(clock, result);
    }

    @Test
    void testAdvanceNanosWithNegative() {
        ManualClock clock = new ManualClock(1000L);
        clock.advanceNanos(-500L);
        assertEquals(500L, clock.nanoTime());
    }
}

