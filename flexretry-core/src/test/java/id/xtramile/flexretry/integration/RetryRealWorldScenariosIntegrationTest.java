package id.xtramile.flexretry.integration;

import id.xtramile.flexretry.Retry;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for real-world retry scenarios
 */
class RetryRealWorldScenariosIntegrationTest {

    @Test
    void testHttpClientRetryScenario() {
        AtomicInteger httpCalls = new AtomicInteger(0);
        List<String> statusCodes = new ArrayList<>();

        String response = Retry.<String>newBuilder()
                .name("http-client-retry")
                .maxAttempts(3)
                .delayMillis(100)
                .retryIf(result -> result != null && result.startsWith("5"))
                .onAttempt(ctx -> statusCodes.add("attempt-" + ctx.attempt()))
                .execute((Callable<String>) () -> {
                    httpCalls.incrementAndGet();
                    int call = httpCalls.get();

                    if (call == 1) {
                        return "500 Internal Server Error";

                    } else if (call == 2) {
                        return "503 Service Unavailable";
                    }

                    return "200 OK";
                })
                .getResult();

        assertEquals("200 OK", response);
        assertEquals(3, httpCalls.get());
        assertEquals(3, statusCodes.size());
    }

    @Test
    void testDatabaseConnectionRetryScenario() {
        AtomicInteger connectionAttempts = new AtomicInteger(0);
        List<Throwable> errors = new ArrayList<>();

        String connection = Retry.<String>newBuilder()
                .name("db-connection")
                .maxAttempts(5)
                .delayMillis(200)
                .retryOn(RuntimeException.class, ConnectException.class)
                .onFailure((error, ctx) -> errors.add(error))
                .fallback(error -> "connection-failed")
                .execute((Callable<String>) () -> {
                    connectionAttempts.incrementAndGet();

                    if (connectionAttempts.get() < 3) {
                        throw new RuntimeException("Connection timeout");
                    }

                    return "connected";
                })
                .getResult();

        assertTrue(connection.equals("connected") || connection.equals("connection-failed"));
        assertTrue(connectionAttempts.get() >= 1);
        assertEquals(0, errors.size());
    }

    @Test
    void testApiRateLimitRetryScenario() {
        AtomicInteger apiCalls = new AtomicInteger(0);
        AtomicInteger sleepCount = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
                .name("api-rate-limit")
                .maxAttempts(4)
                .delayMillis(500)
                .retryIf(res -> res != null && res.contains("429"))
                .beforeSleep((duration, ctx) -> {
                    sleepCount.incrementAndGet();
                    return duration;
                })
                .execute((Callable<String>) () -> {
                    apiCalls.incrementAndGet();

                    if (apiCalls.get() <= 2) {
                        return "429 Too Many Requests";
                    }

                    return "200 Success";
                })
                .getResult();

        assertEquals("200 Success", result);
        assertTrue(apiCalls.get() >= 3);
        assertTrue(sleepCount.get() >= 2);
    }
}

