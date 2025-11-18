package id.xtramile.flexretry;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SleeperTest {

    @Test
    void testSystemSleeper() throws InterruptedException {
        Sleeper sleeper = Sleeper.system();

        assertNotNull(sleeper);

        long start = System.currentTimeMillis();
        sleeper.sleep(Duration.ZERO);
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 10, "Should not sleep for zero duration");

        start = System.currentTimeMillis();
        sleeper.sleep(Duration.ofMillis(-100));
        elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 10, "Should not sleep for negative duration");

        start = System.currentTimeMillis();
        sleeper.sleep(Duration.ofMillis(10));
        elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed >= 10, "Should sleep for at least 10ms");
    }

    @Test
    void testSystemSleeperWithVerySmallDuration() throws InterruptedException {
        Sleeper sleeper = Sleeper.system();

        long start = System.currentTimeMillis();
        sleeper.sleep(Duration.ofNanos(1));
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 10);
    }
}

