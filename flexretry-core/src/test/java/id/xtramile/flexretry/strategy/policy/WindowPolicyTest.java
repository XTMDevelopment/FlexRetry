package id.xtramile.flexretry.strategy.policy;

import id.xtramile.flexretry.support.time.Clock;
import id.xtramile.flexretry.support.time.ManualClock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WindowPolicyTest {

    @Test
    void testConstructor() {
        RetryWindow window = RetryWindow.always();
        Clock clock = Clock.system();
        WindowPolicy<String> policy = new WindowPolicy<>(window, clock);

        assertNotNull(policy);
    }

    @Test
    void testShouldRetryWithAlwaysWindow() {
        RetryWindow window = RetryWindow.always();
        Clock clock = Clock.system();
        WindowPolicy<String> policy = new WindowPolicy<>(window, clock);

        assertTrue(policy.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testShouldRetryWithCustomWindow() {
        RetryWindow window = clock -> true;
        Clock clock = Clock.system();
        WindowPolicy<String> policy = new WindowPolicy<>(window, clock);

        assertTrue(policy.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testShouldRetryWithFalseWindow() {
        RetryWindow window = clock -> false;
        Clock clock = Clock.system();
        WindowPolicy<String> policy = new WindowPolicy<>(window, clock);

        assertFalse(policy.shouldRetry("result", null, 1, 5));
    }

    @Test
    void testShouldRetryWithManualClock() {
        ManualClock clock = new ManualClock(0);
        RetryWindow window = c -> c.nanoTime() < 1000;
        WindowPolicy<String> policy = new WindowPolicy<>(window, clock);

        assertTrue(policy.shouldRetry("result", null, 1, 5));

        clock.advanceNanos(2000);
        assertFalse(policy.shouldRetry("result", null, 1, 5));
    }
}

