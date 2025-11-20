package id.xtramile.flexretry.control.tuning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetrySwitchTest {

    @Test
    void testIsOn_Default() {
        RetrySwitch retrySwitch = new RetrySwitch();
        
        assertTrue(retrySwitch.isOn());
    }

    @Test
    void testSetOn() {
        RetrySwitch retrySwitch = new RetrySwitch();
        
        retrySwitch.setOn(false);
        assertFalse(retrySwitch.isOn());
        
        retrySwitch.setOn(true);
        assertTrue(retrySwitch.isOn());
    }

    @Test
    void testToggle() {
        RetrySwitch retrySwitch = new RetrySwitch();
        
        assertTrue(retrySwitch.isOn());
        
        retrySwitch.setOn(false);
        assertFalse(retrySwitch.isOn());
        
        retrySwitch.setOn(true);
        assertTrue(retrySwitch.isOn());
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        RetrySwitch retrySwitch = new RetrySwitch();
        
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;

            threads[i] = new Thread(() -> {
                retrySwitch.setOn(index % 2 == 0);

                boolean value = retrySwitch.isOn();
                assertEquals(value, (index % 2 == 0));
            });

            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
    }
}

