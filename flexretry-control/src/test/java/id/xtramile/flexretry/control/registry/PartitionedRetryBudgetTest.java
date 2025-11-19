package id.xtramile.flexretry.control.registry;

import id.xtramile.flexretry.control.budget.RetryBudget;
import id.xtramile.flexretry.control.budget.TokenBucketRetryBudget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PartitionedRetryBudgetTest {

    @Test
    void testConstructor() {
        PartitionedRetryBudget<String> partitioned = new PartitionedRetryBudget<>(
            key -> new TokenBucketRetryBudget(10.0, 50.0)
        );
        
        assertNotNull(partitioned);
    }

    @Test
    void testForKey_NewKey() {
        PartitionedRetryBudget<String> partitioned = new PartitionedRetryBudget<>(
            key -> new TokenBucketRetryBudget(10.0, 50.0)
        );
        
        RetryBudget budget = partitioned.forKey("key1");
        
        assertNotNull(budget);
    }

    @Test
    void testForKey_ExistingKey() {
        PartitionedRetryBudget<String> partitioned = new PartitionedRetryBudget<>(
            key -> new TokenBucketRetryBudget(10.0, 50.0)
        );
        
        RetryBudget budget1 = partitioned.forKey("key1");
        RetryBudget budget2 = partitioned.forKey("key1");
        
        assertEquals(budget1, budget2);
    }

    @Test
    void testForKey_DifferentKeys() {
        PartitionedRetryBudget<String> partitioned = new PartitionedRetryBudget<>(
            key -> new TokenBucketRetryBudget(10.0, 50.0)
        );
        
        RetryBudget budget1 = partitioned.forKey("key1");
        RetryBudget budget2 = partitioned.forKey("key2");

        assertNotEquals(budget1, budget2);
    }

    @Test
    void testForKey_IntegerKeys() {
        PartitionedRetryBudget<Integer> partitioned = new PartitionedRetryBudget<>(
            key -> new TokenBucketRetryBudget(10.0 + key, 50.0)
        );
        
        RetryBudget budget1 = partitioned.forKey(1);
        RetryBudget budget2 = partitioned.forKey(2);
        
        assertNotNull(budget1);
        assertNotNull(budget2);
        assertNotEquals(budget1, budget2);
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        PartitionedRetryBudget<String> partitioned = new PartitionedRetryBudget<>(
            key -> new TokenBucketRetryBudget(10.0, 50.0)
        );
        
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        RetryBudget[] results = new RetryBudget[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;

            threads[i] = new Thread(() -> results[index] = partitioned.forKey("key" + (index % 3)));
            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(results[0], results[3]);
        assertEquals(results[1], results[4]);
        assertEquals(results[2], results[5]);
    }
}

