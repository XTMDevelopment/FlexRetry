package id.xtramile.flexretry.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.RetryException;
import id.xtramile.flexretry.strategy.timeout.ExponentialTimeout;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

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
                    () -> Retry.<String>newBuilder()
                        .maxAttempts(2)
                        .attemptTimeout(Duration.ofMillis(50))
                        .attemptExecutor(executor)
                        .retryOn(RuntimeException.class)
                        .execute((Callable<String>) () -> {
                            attempts.incrementAndGet();

                            Thread.sleep(100);

                            return "success";
                        })
                        .getResult());

            assertTrue(attempts.get() >= 1);
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
                    () -> Retry.<String>newBuilder()
                        .maxAttempts(2)
                        .attemptTimeouts(new ExponentialTimeout(Duration.ofMillis(50), 2.0, Duration.ofSeconds(10)))
                        .attemptExecutor(executor)
                        .retryOn(RuntimeException.class)
                        .execute((Callable<String>) () -> {
                            attempts.incrementAndGet();

                            Thread.sleep(200);

                            return "success";
                        })
                        .getResult());

            assertTrue(attempts.get() >= 1);
        } finally {
            executor.shutdown();
        }
    }
}

