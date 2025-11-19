package id.xtramile.flexretry.strategy.backoff;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class DelayClampBackoffTest {

    @Test
    void testConstructor() {
        BackoffStrategy base = new FixedBackoff(Duration.ofMillis(100));
        DelayClampBackoff clamp = new DelayClampBackoff(
                base,
                Duration.ofMillis(50),
                Duration.ofMillis(200)
        );

        assertNotNull(clamp);
    }

    @Test
    void testConstructorWithNullMin() {
        BackoffStrategy base = new FixedBackoff(Duration.ofMillis(100));
        DelayClampBackoff clamp = new DelayClampBackoff(base, null, Duration.ofMillis(200));

        assertNotNull(clamp);
        assertEquals(Duration.ofMillis(100), clamp.delayForAttempt(1));
    }

    @Test
    void testConstructorWithNullMax() {
        BackoffStrategy base = new FixedBackoff(Duration.ofMillis(100));
        DelayClampBackoff clamp = new DelayClampBackoff(base, Duration.ofMillis(50), null);

        assertNotNull(clamp);
        assertEquals(Duration.ofMillis(100), clamp.delayForAttempt(1));
    }

    @Test
    void testDelayForAttemptWithinBounds() {
        BackoffStrategy base = new FixedBackoff(Duration.ofMillis(100));
        DelayClampBackoff clamp = new DelayClampBackoff(
                base,
                Duration.ofMillis(50),
                Duration.ofMillis(200)
        );
        
        Duration delay = clamp.delayForAttempt(1);
        assertEquals(Duration.ofMillis(100), delay);
    }

    @Test
    void testDelayForAttemptBelowMin() {
        BackoffStrategy base = new FixedBackoff(Duration.ofMillis(10));
        DelayClampBackoff clamp = new DelayClampBackoff(
                base,
                Duration.ofMillis(50),
                Duration.ofMillis(200)
        );
        
        Duration delay = clamp.delayForAttempt(1);
        assertEquals(Duration.ofMillis(50), delay);
    }

    @Test
    void testDelayForAttemptAboveMax() {
        BackoffStrategy base = new FixedBackoff(Duration.ofMillis(300));
        DelayClampBackoff clamp = new DelayClampBackoff(
                base,
                Duration.ofMillis(50),
                Duration.ofMillis(200)
        );
        
        Duration delay = clamp.delayForAttempt(1);
        assertEquals(Duration.ofMillis(200), delay);
    }

    @Test
    void testDelayForAttemptAtMin() {
        BackoffStrategy base = new FixedBackoff(Duration.ofMillis(50));
        DelayClampBackoff clamp = new DelayClampBackoff(
                base,
                Duration.ofMillis(50),
                Duration.ofMillis(200)
        );
        
        Duration delay = clamp.delayForAttempt(1);
        assertEquals(Duration.ofMillis(50), delay);
    }

    @Test
    void testDelayForAttemptAtMax() {
        BackoffStrategy base = new FixedBackoff(Duration.ofMillis(200));
        DelayClampBackoff clamp = new DelayClampBackoff(
                base,
                Duration.ofMillis(50),
                Duration.ofMillis(200)
        );
        
        Duration delay = clamp.delayForAttempt(1);
        assertEquals(Duration.ofMillis(200), delay);
    }
}

