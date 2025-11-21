package id.xtramile.flexretry.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.exception.RetryException;
import id.xtramile.flexretry.strategy.policy.RetryWindow;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for window policy
 */
class RetryWindowIntegrationTest {

    @Test
    void testRetryOnlyWhenWindow() {
        RetryWindow window = RetryWindow.always();

        assertThrows(RetryException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(2)
                    .retryOnlyWhen(window)
                    .retryOn(RuntimeException.class)
                    .execute((Callable<String>) () -> {
                        throw new RuntimeException("retry");
                    })
                    .getResult());
    }
}

