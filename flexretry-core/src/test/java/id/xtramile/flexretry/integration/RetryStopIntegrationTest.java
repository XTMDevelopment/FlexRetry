package id.xtramile.flexretry.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.RetryOutcome;
import id.xtramile.flexretry.strategy.stop.MaxElapsedStop;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for stop strategies
 */
class RetryStopIntegrationTest {

    @Test
    void testMaxElapsedTimeStop() {
        AtomicInteger attempts = new AtomicInteger(0);

        RetryOutcome<String> outcome = Retry.<String>newBuilder()
                .maxAttempts(10)
                .stop(new MaxElapsedStop(Duration.ofSeconds(5)))
                .delayMillis(2000)
                .retryOn(RuntimeException.class)
                .execute((Callable<String>) () -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("retry");
                })
                .getOutcome();

        assertFalse(outcome.isSuccess());
        assertTrue(attempts.get() >= 1);
    }
}

