package id.xtramile.flexretry.strategy.policy;

import id.xtramile.flexretry.support.time.Clock;
import id.xtramile.flexretry.support.time.ManualClock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetryWindowTest {

    @Test
    void testAlways() {
        RetryWindow window = RetryWindow.always();
        Clock clock = Clock.system();

        assertTrue(window.allowedNow(clock));
    }

    @Test
    void testAlwaysWithManualClock() {
        RetryWindow window = RetryWindow.always();
        ManualClock clock = new ManualClock(1000);

        assertTrue(window.allowedNow(clock));
    }

    @Test
    void testCustomWindow() {
        RetryWindow window = clock -> clock.nanoTime() > 0;
        Clock clock = Clock.system();

        assertTrue(window.allowedNow(clock));
    }

    @Test
    void testCustomWindowWithCondition() {
        ManualClock clock = new ManualClock(0);
        RetryWindow window = c -> c.nanoTime() < 1000;
        
        assertTrue(window.allowedNow(clock));
        
        clock.advanceNanos(2000);
        assertFalse(window.allowedNow(clock));
    }
}

