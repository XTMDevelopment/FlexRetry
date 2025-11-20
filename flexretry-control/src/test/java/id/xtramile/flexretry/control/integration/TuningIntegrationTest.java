package id.xtramile.flexretry.control.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.RetryException;
import id.xtramile.flexretry.control.RetryControls;
import id.xtramile.flexretry.control.health.HealthProbe;
import id.xtramile.flexretry.control.tuning.DynamicTuning;
import id.xtramile.flexretry.control.tuning.MutableTuning;
import id.xtramile.flexretry.control.tuning.RetrySwitch;
import id.xtramile.flexretry.strategy.backoff.BackoffStrategy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Tuning controls with RetryExecutors.
 * Demonstrates custom DynamicTuning and HealthProbe implementations.
 */
class TuningIntegrationTest {

    /**
     * Custom HealthProbe that tracks health state
     */
    static class SimpleHealthProbe implements HealthProbe {
        private volatile State state = State.UP;

        @Override
        public State state() {
            return state;
        }

        void setState(State state) {
            this.state = state;
        }
    }

    /**
     * Custom DynamicTuning that adjusts maxAttempts based on health
     */
    static class HealthBasedTuning implements DynamicTuning {
        @Override
        public void apply(HealthProbe.State state, Retry.Builder<?> builder) {
            switch (state) {
                case UP:
                    builder.maxAttempts(5);
                    break;
                case DEGRADED:
                    builder.maxAttempts(3);
                    break;
                case DOWN:
                    builder.maxAttempts(1);
                    break;
            }
        }
    }

    /**
     * Custom DynamicTuning that adjusts backoff based on health
     */
    static class BackoffBasedTuning implements DynamicTuning {
        @Override
        public void apply(HealthProbe.State state, Retry.Builder<?> builder) {
            switch (state) {
                case UP:
                    builder.backoff(BackoffStrategy.fixed(Duration.ofMillis(100)));
                    break;
                case DEGRADED:
                    builder.backoff(BackoffStrategy.fixed(Duration.ofMillis(200)));
                    break;
                case DOWN:
                    builder.backoff(BackoffStrategy.fixed(Duration.ofMillis(500)));
                    break;
            }
        }
    }

    /**
     * Custom DynamicTuning that combines multiple adjustments
     */
    static class CompositeTuning implements DynamicTuning {
        @Override
        public void apply(HealthProbe.State state, Retry.Builder<?> builder) {
            switch (state) {
                case UP:
                    builder.maxAttempts(5);
                    builder.backoff(BackoffStrategy.fixed(Duration.ofMillis(50)));
                    break;
                case DEGRADED:
                    builder.maxAttempts(3);
                    builder.backoff(BackoffStrategy.fixed(Duration.ofMillis(100)));
                    break;
                case DOWN:
                    builder.maxAttempts(1);
                    builder.backoff(BackoffStrategy.fixed(Duration.ofMillis(200)));
                    break;
            }
        }
    }

    @Test
    void testRetryWithSwitchStop() {
        RetrySwitch retrySwitch = new RetrySwitch();
        AtomicInteger attemptCount = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
            .maxAttempts(5)
            .stop(RetryControls.switchStop(retrySwitch))
            .retryOn(RuntimeException.class)
            .execute((Callable<String>) () -> {
                attemptCount.incrementAndGet();

                if (attemptCount.get() < 3) {
                    throw new RuntimeException("retry");
                }

                return "success";
            })
            .getResult();

        assertEquals("success", result);
        assertTrue(attemptCount.get() >= 3);

        retrySwitch.setOn(false);

        AtomicInteger attemptCount2 = new AtomicInteger(0);
        RetryException exception = assertThrows(RetryException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(5)
                    .stop(RetryControls.switchStop(retrySwitch))
                    .retryOn(RuntimeException.class)
                    .execute((Callable<String>) () -> {
                        attemptCount2.incrementAndGet();
                        throw new RuntimeException("retry");
                    })
                    .getResult());

        assertEquals(1, attemptCount2.get());
        assertInstanceOf(RuntimeException.class, exception.getCause());
    }

    @Test
    void testRetryWithTunedStop() {
        MutableTuning tuning = new MutableTuning();
        tuning.setMaxAttempts(3);
        tuning.setMaxElapsed(Duration.ofSeconds(30));

        AtomicInteger attemptCount = new AtomicInteger(0);

        String result = Retry.<String>newBuilder()
            .stop(RetryControls.tunedStop(tuning))
            .retryOn(RuntimeException.class)
            .execute((Callable<String>) () -> {
                attemptCount.incrementAndGet();

                if (attemptCount.get() < 2) {
                    throw new RuntimeException("retry");
                }

                return "success";
            })
            .getResult();

        assertEquals("success", result);
        assertTrue(attemptCount.get() >= 2);
    }

    @Test
    void testRetryWithDynamicTuning_HealthBased() {
        SimpleHealthProbe probe = new SimpleHealthProbe();
        HealthBasedTuning tuning = new HealthBasedTuning();
        AtomicInteger attemptCount = new AtomicInteger(0);

        probe.setState(HealthProbe.State.UP);
        Retry.Builder<String> builder = Retry.newBuilder();
        tuning.apply(probe.state(), builder);

        String result = builder
            .retryOn(RuntimeException.class)
            .execute((Callable<String>) () -> {
                attemptCount.incrementAndGet();

                if (attemptCount.get() < 2) {
                    throw new RuntimeException("retry");
                }

                return "success";
            })
            .getResult();

        assertEquals("success", result);
        assertTrue(attemptCount.get() >= 2);
    }

    @Test
    void testRetryWithDynamicTuning_DegradedState() {
        SimpleHealthProbe probe = new SimpleHealthProbe();
        HealthBasedTuning tuning = new HealthBasedTuning();
        AtomicInteger attemptCount = new AtomicInteger(0);

        probe.setState(HealthProbe.State.DEGRADED);
        Retry.Builder<String> builder = Retry.newBuilder();
        tuning.apply(probe.state(), builder);

        String result = builder
            .retryOn(RuntimeException.class)
            .execute((java.util.concurrent.Callable<String>) () -> {
                attemptCount.incrementAndGet();
                if (attemptCount.get() < 2) {
                    throw new RuntimeException("retry");
                }
                return "success";
            })
            .getResult();

        assertEquals("success", result);
        assertTrue(attemptCount.get() >= 2);
    }

    @Test
    void testRetryWithDynamicTuning_DownState() {
        SimpleHealthProbe probe = new SimpleHealthProbe();
        HealthBasedTuning tuning = new HealthBasedTuning();

        probe.setState(HealthProbe.State.DOWN);
        Retry.Builder<String> builder = Retry.newBuilder();
        tuning.apply(probe.state(), builder);

        assertThrows(RuntimeException.class,
                () -> builder
                    .retryOn(RuntimeException.class)
                    .execute((Callable<String>) () -> {
                        throw new RuntimeException("error");
                    })
                    .getResult());
    }

    @Test
    void testRetryWithCustomCompositeTuning() {
        SimpleHealthProbe probe = new SimpleHealthProbe();
        CompositeTuning tuning = new CompositeTuning();
        AtomicInteger attemptCount = new AtomicInteger(0);

        probe.setState(HealthProbe.State.UP);
        Retry.Builder<String> builder = Retry.newBuilder();
        tuning.apply(probe.state(), builder);

        String result = builder
            .retryOn(RuntimeException.class)
            .execute((java.util.concurrent.Callable<String>) () -> {
                attemptCount.incrementAndGet();

                if (attemptCount.get() < 2) {
                    throw new RuntimeException("retry");
                }

                return "success";
            })
            .getResult();

        assertEquals("success", result);
        assertTrue(attemptCount.get() >= 2);
    }
}

