package id.xtramile.flexretry.strategy.backoff;

import id.xtramile.flexretry.support.rand.RandomSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class JitterDecoratorTest {

    @Test
    void testConstructor() {
        BackoffStrategy base = new FixedBackoff(Duration.ofMillis(100));
        JitterDecorator decorator = new JitterDecorator(base, 0.1, null);

        assertNotNull(decorator);
    }

    @Test
    void testConstructorWithInvalidFraction() {
        BackoffStrategy base = new FixedBackoff(Duration.ofMillis(100));
        
        assertThrows(IllegalArgumentException.class,
                () -> new JitterDecorator(base, -0.1, null));
        
        assertThrows(IllegalArgumentException.class,
                () -> new JitterDecorator(base, 1.1, null));
    }

    @Test
    void testDelayForAttempt() {
        BackoffStrategy base = new FixedBackoff(Duration.ofMillis(100));
        JitterDecorator decorator = new JitterDecorator(base, 0.1, null);

        for (int i = 0; i < 10; i++) {
            Duration delay = decorator.delayForAttempt(1);
            assertTrue(delay.toMillis() >= 90 && delay.toMillis() <= 110);
        }
    }

    @Test
    void testDelayForAttemptWithCustomRandomSource() {
        BackoffStrategy base = new FixedBackoff(Duration.ofMillis(100));
        RandomSource rnd = (origin, bound) -> origin + (bound - origin) / 2;
        JitterDecorator decorator = new JitterDecorator(base, 0.1, rnd);
        
        Duration delay = decorator.delayForAttempt(1);

        assertTrue(delay.toMillis() >= 90 && delay.toMillis() <= 110);
    }

    @Test
    void testDelayForAttemptWithZeroFraction() {
        BackoffStrategy base = new FixedBackoff(Duration.ofMillis(100));
        JitterDecorator decorator = new JitterDecorator(base, 0.0, null);

        Duration delay = decorator.delayForAttempt(1);

        assertNotNull(delay);
        assertTrue(delay.toMillis() >= 0);
    }

    @Test
    void testDelayForAttemptWithFullFraction() {
        BackoffStrategy base = new FixedBackoff(Duration.ofMillis(100));
        JitterDecorator decorator = new JitterDecorator(base, 1.0, null);

        for (int i = 0; i < 10; i++) {
            Duration delay = decorator.delayForAttempt(1);
            assertTrue(delay.toMillis() >= 0 && delay.toMillis() <= 200);
        }
    }
}

