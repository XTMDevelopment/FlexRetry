package id.xtramile.flexretry.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.exception.RetryException;
import id.xtramile.flexretry.strategy.timeout.ExponentialTimeout;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for timeout strategies
 */
class RetryTimeoutIntegrationTest {

    @Test
    void testFixedTimeout() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AtomicInteger attempts = new AtomicInteger(0);

            assertThrows(RetryException.class,
                    () -> {
                        long startTime = System.currentTimeMillis();

                        try {
                            Retry.<String>newBuilder()
                                .maxAttempts(2)
                                .attemptTimeout(Duration.ofMillis(100))
                                .attemptExecutor(executor)
                                .retryOn(RuntimeException.class)
                                .execute((Callable<String>) () -> {
                                    int attemptNum = attempts.incrementAndGet();
                                    long attemptStart = System.currentTimeMillis();

                                    Thread.sleep(200);
                                    
                                    long attemptElapsed = System.currentTimeMillis() - attemptStart;
                                    System.out.println("Attempt " + attemptNum + " took " + attemptElapsed + "ms");
                                    
                                    return "success";
                                })
                                .getResult();

                        } finally {
                            long totalElapsed = System.currentTimeMillis() - startTime;
                            System.out.println("Total test execution took " + totalElapsed + "ms");
                        }
                    });

            assertTrue(attempts.get() >= 1, "Should have at least one attempt");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testExponentialTimeout() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AtomicInteger attempts = new AtomicInteger(0);

            assertThrows(RetryException.class,
                    () -> {
                        long startTime = System.currentTimeMillis();

                        try {
                            Retry.<String>newBuilder()
                                .maxAttempts(2)
                                .attemptTimeouts(new ExponentialTimeout(Duration.ofMillis(50), 2.0, Duration.ofSeconds(10)))
                                .attemptExecutor(executor)
                                .retryOn(RuntimeException.class)
                                .execute((Callable<String>) () -> {
                                    int attemptNum = attempts.incrementAndGet();
                                    long attemptStart = System.currentTimeMillis();

                                    Thread.sleep(200);
                                    
                                    long attemptElapsed = System.currentTimeMillis() - attemptStart;
                                    System.out.println("Attempt " + attemptNum + " took " + attemptElapsed + "ms");
                                    
                                    return "success";
                                })
                                .getResult();

                        } finally {
                            long totalElapsed = System.currentTimeMillis() - startTime;
                            System.out.println("Total test execution took " + totalElapsed + "ms");
                        }
                    });

            assertTrue(attempts.get() >= 1, "Should have at least one attempt");
        } finally {
            executor.shutdown();
        }
    }
}

