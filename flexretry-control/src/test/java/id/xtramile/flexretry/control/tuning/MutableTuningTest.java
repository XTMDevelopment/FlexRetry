package id.xtramile.flexretry.control.tuning;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class MutableTuningTest {

    @Test
    void testMaxAttempts_Default() {
        MutableTuning tuning = new MutableTuning();
        
        assertEquals(3, tuning.maxAttempts());
    }

    @Test
    void testSetMaxAttempts() {
        MutableTuning tuning = new MutableTuning();
        
        tuning.setMaxAttempts(5);
        assertEquals(5, tuning.maxAttempts());
        
        tuning.setMaxAttempts(10);
        assertEquals(10, tuning.maxAttempts());
    }

    @Test
    void testMaxElapsed_Default() {
        MutableTuning tuning = new MutableTuning();
        
        assertNull(tuning.maxElapsed());
    }

    @Test
    void testSetMaxElapsed() {
        MutableTuning tuning = new MutableTuning();
        
        Duration duration = Duration.ofSeconds(30);
        tuning.setMaxElapsed(duration);
        
        assertEquals(duration, tuning.maxElapsed());
    }

    @Test
    void testSetMaxElapsed_Null() {
        MutableTuning tuning = new MutableTuning();
        
        tuning.setMaxElapsed(Duration.ofSeconds(30));
        assertNotNull(tuning.maxElapsed());
        
        tuning.setMaxElapsed(null);
        assertNull(tuning.maxElapsed());
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        MutableTuning tuning = new MutableTuning();
        
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int value = i;

            threads[i] = new Thread(() -> {
                tuning.setMaxAttempts(value);

                int result = tuning.maxAttempts();
                assertTrue(result >= 0 && result < threadCount);
            });

            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
    }
}

