package id.xtramile.flexretry.integration;

import id.xtramile.flexretry.Retry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for async execution
 */
class RetryAsyncIntegrationTest {

    @Test
    void testAsyncExecution() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AtomicInteger attempts = new AtomicInteger(0);

            CompletableFuture<String> future = Retry.<String>newBuilder()
                    .maxAttempts(3)
                    .retryOn(RuntimeException.class)
                    .execute((Callable<String>) () -> {
                        attempts.incrementAndGet();

                        if (attempts.get() < 2) {
                            throw new RuntimeException("retry");
                        }

                        return "async-success";
                    })
                    .getResultAsync(executor);

            String result = future.get();
            assertEquals("async-success", result);
            assertEquals(2, attempts.get());

        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testAsyncExecutionWithFailure() {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            CompletableFuture<String> future = Retry.<String>newBuilder()
                    .maxAttempts(2)
                    .retryOn(RuntimeException.class)
                    .execute((Callable<String>) () -> {
                        throw new RuntimeException("always fail");
                    })
                    .getResultAsync(executor);

            assertThrows(Exception.class, future::get);
        } finally {
            executor.shutdown();
        }
    }
}

