package id.xtramile.flexretry.control.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.control.RetryControls;
import id.xtramile.flexretry.control.sf.SingleFlight;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SingleFlight with RetryExecutors.
 */
class SingleFlightIntegrationTest {

    @Test
    void testRetryWithSingleFlight() {
        SingleFlight<String> sf = new SingleFlight<>();
        AtomicInteger callCount = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
            .name("test")
            .id("test-id")
            .maxAttempts(3)
            .execute(RetryControls.singleFlight(
                sf,
                Retry.<String>newBuilder().name("test").id("test-id").execute((Callable<String>) () -> "dummy").toConfig(),
                ctx -> ctx.id() + ":" + ctx.name(),
                () -> {
                    callCount.incrementAndGet();
                    return "success";
                }
            ))
            .getResult();

        assertEquals("success", result);
        assertEquals(1, callCount.get());
    }

    @Test
    void testRetryWithSingleFlight_DeduplicatesConcurrentCalls() throws Exception {
        SingleFlight<String> sf = new SingleFlight<>();
        AtomicInteger callCount = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(5);

        String[] results = new String[5];
        Thread[] threads = new Thread[5];

        for (int i = 0; i < 5; i++) {
            final int index = i;

            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();

                    results[index] = Retry.<String>newBuilder()
                        .name("test")
                        .id("test-id")
                        .maxAttempts(1)
                        .execute(RetryControls.singleFlight(
                            sf,
                            Retry.<String>newBuilder()
                                    .name("test")
                                    .id("test-id")
                                    .execute((Callable<String>) () -> "dummy")
                                    .toConfig(),
                            ctx -> ctx.id() + ":" + ctx.name(),
                            () -> {
                                callCount.incrementAndGet();

                                try {
                                    Thread.sleep(50);

                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }

                                return "success";
                            }
                        ))
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

        for (String result : results) {
            assertEquals("success", result);
        }

        assertTrue(callCount.get() >= 1 && callCount.get() <= 5);
    }

    @Test
    void testRetryWithSingleFlight_DifferentKeys() {
        SingleFlight<String> sf = new SingleFlight<>();
        AtomicInteger callCount = new AtomicInteger(0);

        String result1 = Retry.<String>newBuilder()
            .name("test1")
            .id("test-id1")
            .maxAttempts(1)
            .execute(RetryControls.singleFlight(
                sf,
                Retry.<String>newBuilder()
                        .name("test1")
                        .id("test-id1")
                        .execute((Callable<String>) () -> "dummy")
                        .toConfig(),
                ctx -> ctx.id() + ":" + ctx.name(),
                () -> {
                    callCount.incrementAndGet();
                    return "success1";
                }
            ))
            .getResult();

        String result2 = Retry.<String>newBuilder()
            .name("test2")
            .id("test-id2")
            .maxAttempts(1)
            .execute(RetryControls.singleFlight(
                sf,
                Retry.<String>newBuilder()
                        .name("test2")
                        .id("test-id2")
                        .execute((Callable<String>) () -> "dummy")
                        .toConfig(),
                ctx -> ctx.id() + ":" + ctx.name(),
                () -> {
                    callCount.incrementAndGet();
                    return "success2";
                }
            ))
            .getResult();

        assertEquals("success1", result1);
        assertEquals("success2", result2);
        assertEquals(2, callCount.get());
    }

    @Test
    void testRetryWithSingleFlight_ExceptionPropagates() {
        SingleFlight<String> sf = new SingleFlight<>();

        assertThrows(RuntimeException.class,
                () -> Retry.<String>newBuilder()
                    .name("test")
                    .id("test-id")
                    .maxAttempts(1)
                    .execute(RetryControls.singleFlight(
                        sf,
                        Retry.<String>newBuilder()
                                .name("test")
                                .id("test-id")
                                .execute((Callable<String>) () -> "dummy")
                                .toConfig(),
                        ctx -> ctx.id() + ":" + ctx.name(),
                        () -> {
                            throw new RuntimeException("test error");
                        }
                    ))
                    .getResult());
    }
}