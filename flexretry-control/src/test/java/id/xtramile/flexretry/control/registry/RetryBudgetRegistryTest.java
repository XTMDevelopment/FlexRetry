package id.xtramile.flexretry.control.registry;

import id.xtramile.flexretry.control.budget.RetryBudget;
import id.xtramile.flexretry.control.budget.TokenBucketRetryBudget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetryBudgetRegistryTest {

    @Test
    void testGetOrRegister_NewKey() {
        RetryBudgetRegistry registry = new RetryBudgetRegistry();
        RetryBudget budget = new TokenBucketRetryBudget(10.0, 50.0);
        
        RetryBudget result = registry.getOrRegister("key1", budget);
        
        assertEquals(budget, result);
    }

    @Test
    void testGetOrRegister_ExistingKey() {
        RetryBudgetRegistry registry = new RetryBudgetRegistry();
        RetryBudget budget1 = new TokenBucketRetryBudget(10.0, 50.0);
        RetryBudget budget2 = new TokenBucketRetryBudget(20.0, 100.0);
        
        RetryBudget result1 = registry.getOrRegister("key1", budget1);
        RetryBudget result2 = registry.getOrRegister("key1", budget2);
        
        assertEquals(budget1, result1);
        assertEquals(budget1, result2);
        assertNotEquals(budget2, result2);
    }

    @Test
    void testGetOrRegister_DifferentKeys() {
        RetryBudgetRegistry registry = new RetryBudgetRegistry();
        RetryBudget budget1 = new TokenBucketRetryBudget(10.0, 50.0);
        RetryBudget budget2 = new TokenBucketRetryBudget(20.0, 100.0);
        
        RetryBudget result1 = registry.getOrRegister("key1", budget1);
        RetryBudget result2 = registry.getOrRegister("key2", budget2);
        
        assertEquals(budget1, result1);
        assertEquals(budget2, result2);
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        RetryBudgetRegistry registry = new RetryBudgetRegistry();
        
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        RetryBudget[] results = new RetryBudget[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;

            threads[i] = new Thread(() -> {
                RetryBudget budget = new TokenBucketRetryBudget(10.0 + index, 50.0);
                results[index] = registry.getOrRegister("sharedKey", budget);
            });

            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }

        RetryBudget first = results[0];
        for (RetryBudget result : results) {
            assertEquals(first, result);
        }
    }
}

