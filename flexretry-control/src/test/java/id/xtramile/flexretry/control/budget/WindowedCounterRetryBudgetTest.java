package id.xtramile.flexretry.control.budget;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class WindowedCounterRetryBudgetTest {

    @Test
    void testConstructor_ValidParameters() {
        WindowedCounterRetryBudget budget = new WindowedCounterRetryBudget(10, 1, TimeUnit.SECONDS);
        assertEquals(10, budget.limit());
        assertTrue(budget.windowNanos() > 0);
    }

    @Test
    void testConstructor_NegativeLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> new WindowedCounterRetryBudget(-1, 1, TimeUnit.SECONDS));
    }

    @Test
    void testConstructor_ZeroWindowDuration() {
        assertThrows(IllegalArgumentException.class,
                () -> new WindowedCounterRetryBudget(10, 0, TimeUnit.SECONDS));
    }

    @Test
    void testConstructor_NegativeWindowDuration() {
        assertThrows(IllegalArgumentException.class,
                () -> new WindowedCounterRetryBudget(10, -1, TimeUnit.SECONDS));
    }

    @Test
    void testTryAcquire_WithinLimit() {
        WindowedCounterRetryBudget budget = new WindowedCounterRetryBudget(5, 1, TimeUnit.SECONDS);
        
        for (int i = 0; i < 5; i++) {
            assertTrue(budget.tryAcquire());
        }
        
        assertFalse(budget.tryAcquire());
    }

    @Test
    void testTryAcquire_WindowReset() throws InterruptedException {
        WindowedCounterRetryBudget budget = new WindowedCounterRetryBudget(3, 100, TimeUnit.MILLISECONDS);

        assertTrue(budget.tryAcquire());
        assertTrue(budget.tryAcquire());
        assertTrue(budget.tryAcquire());
        assertFalse(budget.tryAcquire());

        Thread.sleep(150);

        assertTrue(budget.tryAcquire());
        assertTrue(budget.tryAcquire());
        assertTrue(budget.tryAcquire());
        assertFalse(budget.tryAcquire());
    }

    @Test
    void testRemainingInWindow() {
        WindowedCounterRetryBudget budget = new WindowedCounterRetryBudget(5, 1, TimeUnit.SECONDS);
        
        assertEquals(5, budget.remainingInWindow());
        
        budget.tryAcquire();
        assertEquals(4, budget.remainingInWindow());
        
        budget.tryAcquire();
        assertEquals(3, budget.remainingInWindow());
        
        budget.tryAcquire();
        assertEquals(2, budget.remainingInWindow());
    }

    @Test
    void testRemainingInWindow_AfterExhaustion() {
        WindowedCounterRetryBudget budget = new WindowedCounterRetryBudget(3, 1, TimeUnit.SECONDS);
        
        budget.tryAcquire();
        budget.tryAcquire();
        budget.tryAcquire();
        
        assertEquals(0, budget.remainingInWindow());
    }

    @Test
    void testRemainingInWindow_AfterReset() throws InterruptedException {
        WindowedCounterRetryBudget budget = new WindowedCounterRetryBudget(3, 100, TimeUnit.MILLISECONDS);
        
        budget.tryAcquire();
        budget.tryAcquire();
        budget.tryAcquire();
        assertEquals(0, budget.remainingInWindow());
        
        Thread.sleep(150);
        
        assertEquals(3, budget.remainingInWindow());
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        WindowedCounterRetryBudget budget = new WindowedCounterRetryBudget(10, 1, TimeUnit.SECONDS);
        
        int threadCount = 5;
        int acquiresPerThread = 5;
        Thread[] threads = new Thread[threadCount];
        boolean[] results = new boolean[threadCount * acquiresPerThread];
        int[] index = {0};
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < acquiresPerThread; j++) {
                    synchronized (index) {
                        results[index[0]++] = budget.tryAcquire();
                    }
                }
            });

            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }

        int successes = 0;
        for (boolean result : results) {
            if (result) successes++;
        }

        assertEquals(10, successes);
    }

    @Test
    void testZeroLimit() {
        WindowedCounterRetryBudget budget = new WindowedCounterRetryBudget(0, 1, TimeUnit.SECONDS);
        
        assertFalse(budget.tryAcquire());
        assertEquals(0, budget.remainingInWindow());
    }
}

