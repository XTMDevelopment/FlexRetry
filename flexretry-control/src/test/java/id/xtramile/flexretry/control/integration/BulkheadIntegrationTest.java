package id.xtramile.flexretry.control.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.RetryException;
import id.xtramile.flexretry.control.RetryControls;
import id.xtramile.flexretry.control.bulkhead.Bulkhead;
import id.xtramile.flexretry.control.bulkhead.BulkheadFullException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Bulkhead with RetryExecutors.
 */
class BulkheadIntegrationTest {

    @Test
    void testRetryWithBulkhead() {
        Bulkhead bulkhead = new Bulkhead(2);
        AtomicInteger successCount = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
            .maxAttempts(3)
            .execute(RetryControls.bulkhead(bulkhead, (Supplier<String>) () -> {
                successCount.incrementAndGet();
                return "success";
            }))
            .getResult();

        assertEquals("success", result);
        assertEquals(1, successCount.get());
    }

    @Test
    void testRetryWithBulkhead_Full() {
        Bulkhead bulkhead = new Bulkhead(1);
        bulkhead.tryAcquire();

        RetryException exception = assertThrows(RetryException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(3)
                    .execute(RetryControls.bulkhead(bulkhead, (Supplier<String>) () -> "success"))
                    .getResult());

        assertInstanceOf(BulkheadFullException.class, exception.getCause());
    }

    @Test
    void testRetryWithBulkhead_ConcurrentRequests() throws Exception {
        Bulkhead bulkhead = new Bulkhead(2);
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(3);

        String[] results = new String[3];
        Thread[] threads = new Thread[3];

        for (int i = 0; i < 3; i++) {
            final int index = i;

            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();

                    results[index] = Retry.<String>newBuilder()
                        .maxAttempts(1)
                        .execute(RetryControls.bulkhead(bulkhead, (Supplier<String>) () -> {
                            successCount.incrementAndGet();
                            return "success" + index;
                        }))
                        .getResult();

                } catch (Exception e) {
                    e.printStackTrace();

                } finally {
                    doneLatch.countDown();
                }
            });

            threads[i].start();
        }

        Thread.sleep(10);
        startLatch.countDown();
        doneLatch.await();

        int success = 0;
        for (String result : results) {
            if (result != null && result.startsWith("success")) {
                success++;
            }
        }

        assertTrue(success >= 2);
    }
}