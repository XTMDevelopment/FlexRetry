package id.xtramile.flexretry.strategy.backoff;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ResettableBackoffTest {

    @Test
    void testConstructor() {
        BackoffStrategy base = new FixedBackoff(Duration.ofMillis(100));
        ResettableBackoff resettable = new ResettableBackoff(base);
        assertNotNull(resettable);
    }

    @Test
    void testDelayForAttempt() {
        BackoffStrategy base = new FixedBackoff(Duration.ofMillis(100));
        ResettableBackoff resettable = new ResettableBackoff(base);

        Duration delay1 = resettable.delayForAttempt(100); // attempt parameter ignored
        Duration delay2 = resettable.delayForAttempt(200); // attempt parameter ignored

        assertEquals(delay1, delay2);
    }

    @Test
    void testReset() {
        ExponentialBackoff base = new ExponentialBackoff(Duration.ofMillis(100), 2.0);
        ResettableBackoff resettable = new ResettableBackoff(base);

        Duration delay1 = resettable.delayForAttempt(1);
        assertEquals(Duration.ofMillis(100), delay1);

        Duration delay2 = resettable.delayForAttempt(1);
        assertEquals(Duration.ofMillis(200), delay2);

        Duration delay3 = resettable.delayForAttempt(1);
        assertEquals(Duration.ofMillis(400), delay3);

        resettable.reset();

        Duration delay4 = resettable.delayForAttempt(1);
        assertEquals(Duration.ofMillis(100), delay4);
    }

    @Test
    void testResetMultipleTimes() {
        ExponentialBackoff base = new ExponentialBackoff(Duration.ofMillis(100), 2.0);
        ResettableBackoff resettable = new ResettableBackoff(base);
        
        resettable.delayForAttempt(1);
        resettable.delayForAttempt(1);
        resettable.reset();
        resettable.delayForAttempt(1);
        resettable.reset();
        
        Duration delay = resettable.delayForAttempt(1);
        assertEquals(Duration.ofMillis(100), delay);
    }
}

