package id.xtramile.flexretry.support.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClockTest {

    @Test
    void testSystem() {
        Clock clock = Clock.system();
        assertNotNull(clock);
        
        long time1 = clock.nanoTime();
        assertTrue(time1 >= 0);
        
        long time2 = clock.nanoTime();
        assertTrue(time2 >= time1);
    }

    @Test
    void testSystemReturnsIncreasingValues() {
        Clock clock = Clock.system();
        
        long time1 = clock.nanoTime();
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long time2 = clock.nanoTime();
        
        assertTrue(time2 >= time1);
    }
}

