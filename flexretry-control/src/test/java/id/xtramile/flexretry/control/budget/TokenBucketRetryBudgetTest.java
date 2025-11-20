package id.xtramile.flexretry.control.budget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketRetryBudgetTest {

    @Test
    void testConstructor_ValidParameters() {
        TokenBucketRetryBudget budget = new TokenBucketRetryBudget(10.0, 50.0);
        assertEquals(10.0, budget.ratePerSecond());
        assertEquals(50.0, budget.capacity());
    }

    @Test
    void testConstructor_NegativeTokensPerSecond() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenBucketRetryBudget(-1.0, 50.0));
    }

    @Test
    void testConstructor_ZeroCapacity() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenBucketRetryBudget(10.0, 0.0));
    }

    @Test
    void testConstructor_NegativeCapacity() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenBucketRetryBudget(10.0, -1.0));
    }

    @Test
    void testTryAcquire_InitialCapacity() {
        TokenBucketRetryBudget budget = new TokenBucketRetryBudget(10.0, 5.0);

        for (int i = 0; i < 5; i++) {
            assertTrue(budget.tryAcquire());
        }

        assertFalse(budget.tryAcquire());
    }

    @Test
    void testTryAcquire_Refill() throws InterruptedException {
        TokenBucketRetryBudget budget = new TokenBucketRetryBudget(10.0, 5.0);

        for (int i = 0; i < 5; i++) {
            assertTrue(budget.tryAcquire());
        }

        assertFalse(budget.tryAcquire());

        Thread.sleep(150);

        assertTrue(budget.tryAcquire());
    }

    @Test
    void testAvailableTokens() {
        TokenBucketRetryBudget budget = new TokenBucketRetryBudget(10.0, 5.0);
        
        assertEquals(5.0, budget.availableTokens(), 0.01);
        
        budget.tryAcquire();
        assertEquals(4.0, budget.availableTokens(), 0.01);
        
        budget.tryAcquire();
        assertEquals(3.0, budget.availableTokens(), 0.01);
    }

    @Test
    void testAvailableTokens_Refill() throws InterruptedException {
        TokenBucketRetryBudget budget = new TokenBucketRetryBudget(10.0, 5.0);

        for (int i = 0; i < 5; i++) {
            budget.tryAcquire();
        }
        assertEquals(0.0, budget.availableTokens(), 0.01);

        Thread.sleep(150);

        assertTrue(budget.availableTokens() > 0);
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        TokenBucketRetryBudget budget = new TokenBucketRetryBudget(10.0, 50.0);
        
        int threadCount = 10;
        int acquiresPerThread = 10;
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

        assertTrue(successes > 0, "Should have at least some successful acquires");
        assertTrue(successes <= 55, "Should not exceed capacity plus small refill allowance, got: " + successes);
    }
}

