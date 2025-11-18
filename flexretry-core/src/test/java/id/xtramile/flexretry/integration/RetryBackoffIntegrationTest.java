package id.xtramile.flexretry.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.strategy.backoff.BackoffRouter;
import id.xtramile.flexretry.strategy.backoff.ExponentialBackoff;
import id.xtramile.flexretry.strategy.backoff.FixedBackoff;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for backoff strategies
 */
class RetryBackoffIntegrationTest {

    @Test
    void testExponentialBackoff() {
        List<Duration> delays = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger(0);

        Retry.<String>newBuilder()
                .maxAttempts(4)
                .backoff(new ExponentialBackoff(Duration.ofMillis(10), 2.0))
                .retryOn(RuntimeException.class)
                .beforeSleep((duration, ctx) -> {
                    delays.add(duration);
                    return duration;
                })
                .execute((Callable<String>) () -> {
                    attempts.incrementAndGet();

                    if (attempts.get() < 4) {
                        throw new RuntimeException("retry");
                    }

                    return "success";
                })
                .getResult();

        assertEquals(3, delays.size());
        assertTrue(delays.get(0).toMillis() >= 10);
        assertTrue(delays.get(1).toMillis() >= 20);
        assertTrue(delays.get(2).toMillis() >= 40);
    }

    @Test
    void testBackoffRouterWithDifferentExceptions() {
        BackoffRouter router = new BackoffRouter();
        router.when(e -> e instanceof IllegalArgumentException, new FixedBackoff(Duration.ofMillis(50)));
        router.when(e -> e instanceof RuntimeException, new FixedBackoff(Duration.ofMillis(100)));

        List<Duration> delays = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger(0);

        Retry.<String>newBuilder()
                .maxAttempts(3)
                .backoff(new FixedBackoff(Duration.ofMillis(10)))
                .backoffRouter(router)
                .retryOn(RuntimeException.class, IllegalArgumentException.class)
                .beforeSleep((duration, ctx) -> {
                    delays.add(duration);
                    return duration;
                })
                .execute((Callable<String>) () -> {
                    attempts.incrementAndGet();

                    if (attempts.get() == 1) {
                        throw new IllegalArgumentException("error1");
                    }

                    if (attempts.get() == 2) {
                        throw new RuntimeException("error2");
                    }

                    return "success";
                })
                .getResult();

        assertEquals(2, delays.size());
    }
}

