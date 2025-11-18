package id.xtramile.flexretry;

import id.xtramile.flexretry.lifecycle.AttemptLifecycle;
import id.xtramile.flexretry.strategy.backoff.BackoffRouter;
import id.xtramile.flexretry.strategy.backoff.BackoffStrategy;
import id.xtramile.flexretry.strategy.backoff.FixedBackoff;
import id.xtramile.flexretry.strategy.policy.RetryPolicy;
import id.xtramile.flexretry.strategy.stop.FixedAttemptsStop;
import id.xtramile.flexretry.strategy.stop.StopStrategy;
import id.xtramile.flexretry.strategy.timeout.AttemptTimeoutStrategy;
import id.xtramile.flexretry.strategy.timeout.FixedTimeout;
import id.xtramile.flexretry.support.time.Clock;
import id.xtramile.flexretry.support.time.ManualClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class RetryExecutorTest {

    @Test
    void testRunSuccess() {
        RetryExecutor<String> executor = createExecutor(
                new FixedAttemptsStop(3),
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> false,
                () -> "success"
        );

        String result = executor.run();
        assertEquals("success", result);
    }

    @Test
    void testRunWithOutcomeSuccess() {
        RetryExecutor<String> executor = createExecutor(
                new FixedAttemptsStop(3),
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> false,
                () -> "success"
        );

        RetryOutcome<String> outcome = executor.runWithOutcome();
        assertTrue(outcome.isSuccess());
        assertEquals("success", outcome.result());
        assertEquals(1, outcome.attempts());
    }

    @Test
    void testRunFailure() {
        RetryExecutor<String> executor = createExecutor(
                new FixedAttemptsStop(2),
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> false,
                () -> {
                    throw new RuntimeException("error");
                }
        );

        assertThrows(RetryException.class, executor::run);
    }

    @Test
    void testRunWithOutcomeFailure() {
        RetryExecutor<String> executor = createExecutor(
                new FixedAttemptsStop(2),
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> error != null && attempt < maxAttempts,
                () -> {
                    throw new RuntimeException("error");
                }
        );

        RetryOutcome<String> outcome = executor.runWithOutcome();
        assertFalse(outcome.isSuccess());
        assertNotNull(outcome.error());
        assertEquals(2, outcome.attempts());
    }

    @Test
    void testRetryOnException() {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryExecutor<String> executor = createExecutor(
                new FixedAttemptsStop(3),
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> error != null && attempt < maxAttempts,
                () -> {
                    attempts.incrementAndGet();
                    if (attempts.get() < 3) {
                        throw new RuntimeException("retry");
                    }
                    return "success";
                }
        );

        String result = executor.run();
        assertEquals("success", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void testRetryOnResult() {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryExecutor<String> executor = createExecutor(
                new FixedAttemptsStop(3),
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> result != null && result.equals("retry") && attempt < maxAttempts,
                () -> {
                    attempts.incrementAndGet();
                    return attempts.get() < 3 ? "retry" : "success";
                }
        );

        String result = executor.run();
        assertEquals("success", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void testFallback() {
        RetryExecutor<String> executor = createExecutor(
                new FixedAttemptsStop(2),
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> false,
                () -> {
                    throw new RuntimeException("error");
                },
                error -> "fallback"
        );

        String result = executor.run();
        assertEquals("fallback", result);
    }

    @Test
    void testRunWithOutcomeFallback() {
        RetryExecutor<String> executor = createExecutor(
                new FixedAttemptsStop(2),
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> false,
                () -> {
                    throw new RuntimeException("error");
                },
                error -> "fallback"
        );

        RetryOutcome<String> outcome = executor.runWithOutcome();
        assertTrue(outcome.isSuccess());
        assertEquals("fallback", outcome.result());
    }

    @Test
    void testListeners() {
        AtomicInteger onAttempt = new AtomicInteger(0);
        AtomicInteger onSuccess = new AtomicInteger(0);
        AtomicInteger onFailure = new AtomicInteger(0);
        AtomicInteger onFinally = new AtomicInteger(0);

        RetryListeners<String> listeners = new RetryListeners<>();
        listeners.onAttempt = ctx -> onAttempt.incrementAndGet();
        listeners.onSuccess = (result, ctx) -> onSuccess.incrementAndGet();
        listeners.onFailure = (error, ctx) -> onFailure.incrementAndGet();
        listeners.onFinally = ctx -> onFinally.incrementAndGet();

        RetryPolicy<String> policy = (result, error, attempt, maxAttempts) -> false;
        RetryExecutor<String> executor = new RetryExecutor<String>(
                "test", "id", Map.of(),
                new FixedAttemptsStop(1),
                new FixedBackoff(Duration.ZERO),
                policy,
                listeners,
                Sleeper.system(),
                Clock.system(),
                null, null,
                () -> "success",
                null,
                null, null, null
        );

        executor.run();
        assertTrue(onAttempt.get() > 0);
        assertEquals(1, onSuccess.get());
        assertEquals(1, onFinally.get());
    }

    @Test
    void testLifecycle() {
        AtomicInteger beforeAttempt = new AtomicInteger(0);
        AtomicInteger afterSuccess = new AtomicInteger(0);
        AtomicInteger afterFailure = new AtomicInteger(0);

        AttemptLifecycle<String> lifecycle = new AttemptLifecycle<>() {
            @Override
            public void beforeAttempt(RetryContext<String> ctx) {
                beforeAttempt.incrementAndGet();
            }

            @Override
            public void afterSuccess(RetryContext<String> ctx) {
                afterSuccess.incrementAndGet();
            }

            @Override
            public void afterFailure(RetryContext<String> ctx, Throwable error) {
                afterFailure.incrementAndGet();
            }
        };

        RetryPolicy<String> policy = (result, error, attempt, maxAttempts) -> false;
        RetryExecutor<String> executor = new RetryExecutor<>(
                "test", "id", Map.of(),
                new FixedAttemptsStop(1),
                new FixedBackoff(Duration.ZERO),
                policy,
                new RetryListeners<>(),
                Sleeper.system(),
                Clock.system(),
                null, null,
                () -> "success",
                null,
                null, lifecycle, null
        );

        executor.run();
        assertTrue(beforeAttempt.get() > 0);
        assertEquals(1, afterSuccess.get());
    }

    @Test
    void testBackoffRouter() {
        BackoffRouter router = new BackoffRouter();
        BackoffStrategy errorBackoff = new FixedBackoff(Duration.ofMillis(100));
        router.when(e -> e instanceof RuntimeException, errorBackoff);

        RetryExecutor<String> executor = new RetryExecutor<>(
                "test", "id", Map.of(),
                new FixedAttemptsStop(2),
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> error != null && attempt < maxAttempts,
                new RetryListeners<>(),
                Sleeper.system(),
                Clock.system(),
                null, null,
                () -> {
                    throw new RuntimeException("error");
                },
                null, router, null, null
        );

        assertThrows(RetryException.class, executor::run);
    }

    @Test
    void testAttemptTimeout() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            RetryExecutor<String> executor = new RetryExecutor<>(
                    "test", "id", Map.of(),
                    new FixedAttemptsStop(1),
                    new FixedBackoff(Duration.ZERO),
                    (result, error, attempt, maxAttempts) -> false,
                    new RetryListeners<>(),
                    Sleeper.system(),
                    Clock.system(),
                    Duration.ofMillis(50),
                    executorService,
                    () -> {
                        Thread.sleep(100);
                        return "success";
                    },
                    null, null, null, null
            );

            assertThrows(RetryException.class, executor::run);
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    void testAttemptTimeoutStrategy() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            AttemptTimeoutStrategy timeoutStrategy = new FixedTimeout(Duration.ofMillis(50));

            RetryExecutor<String> executor = new RetryExecutor<>(
                    "test", "id", Map.of(),
                    new FixedAttemptsStop(1),
                    new FixedBackoff(Duration.ZERO),
                    (result, error, attempt, maxAttempts) -> false,
                    new RetryListeners<>(),
                    Sleeper.system(),
                    Clock.system(),
                    null,
                    executorService,
                    () -> {
                        Thread.sleep(100);
                        return "success";
                    },
                    null, null, null, timeoutStrategy
            );

            assertThrows(RetryException.class, executor::run);
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    void testUnwrapRuntimeException() {
        RetryExecutor<String> executor = createExecutor(
                new FixedAttemptsStop(1),
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> false,
                () -> {
                    throw new RuntimeException(new IllegalArgumentException("cause"));
                }
        );

        RetryOutcome<String> outcome = executor.runWithOutcome();
        assertNotNull(outcome.error());
        assertInstanceOf(IllegalArgumentException.class, outcome.error());
    }

    @Test
    void testUnwrapCompletionException() {
        RetryExecutor<String> executor = createExecutor(
                new FixedAttemptsStop(1),
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> false,
                () -> {
                    throw new CompletionException(new IllegalArgumentException("cause"));
                }
        );

        RetryOutcome<String> outcome = executor.runWithOutcome();
        assertNotNull(outcome.error());
        assertInstanceOf(IllegalArgumentException.class, outcome.error());
    }

    @Test
    void testInterruptedException() {
        RetryExecutor<String> executor = createExecutor(
                new FixedAttemptsStop(3),
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> true,
                () -> {
                    Thread.currentThread().interrupt();
                    throw new InterruptedException("interrupted");
                }
        );

        RetryOutcome<String> outcome = executor.runWithOutcome();
        assertFalse(outcome.isSuccess());
        assertInstanceOf(InterruptedException.class, outcome.error());
    }

    @Test
    void testStopBeforeAttempt() {
        ManualClock clock = new ManualClock(0);
        StopStrategy stop = new StopStrategy() {
            @Override
            public boolean shouldStop(int attempt, long startNanos, long nowNanos, Duration nextDelay) {
                return attempt > 1;
            }

            @Override
            public Optional<Integer> maxAttempts() {
                return java.util.Optional.empty();
            }
        };

        RetryExecutor<String> executor = new RetryExecutor<>(
                "test", "id", Map.of(),
                stop,
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> true,
                new RetryListeners<>(),
                Sleeper.system(),
                clock,
                null, null,
                () -> "success",
                null, null, null, null
        );

        RetryOutcome<String> outcome = executor.runWithOutcome();
        assertFalse(outcome.isSuccess());
    }

    @Test
    void testBeforeSleep() {
        AtomicInteger sleepCount = new AtomicInteger(0);
        RetryListeners<String> listeners = new RetryListeners<>();
        listeners.beforeSleep = (duration, ctx) -> {
            sleepCount.incrementAndGet();
            return duration;
        };

        AtomicInteger taskAttempts = new AtomicInteger(0);
        RetryPolicy<String> policy = (result, error, attempt, maxAttempts)
                -> "retry".equals(result) && attempt < maxAttempts;

        RetryExecutor<String> executor = new RetryExecutor<>(
                "test", "id", Map.of(),
                new FixedAttemptsStop(2),
                new FixedBackoff(Duration.ofMillis(10)),
                policy,
                listeners,
                Sleeper.system(),
                Clock.system(),
                null, null,
                () -> {
                    taskAttempts.incrementAndGet();
                    return "retry";
                },
                null,
                null, null, null
        );

        String result = executor.run();
        assertEquals("retry", result);
        assertTrue(sleepCount.get() > 0);
    }

    @Test
    void testNullSafe() {
        RetryListeners<String> listeners = new RetryListeners<>();
        listeners.beforeSleep = (duration, ctx) -> {
            throw new RuntimeException("error in beforeSleep");
        };

        AtomicInteger taskAttempts = new AtomicInteger(0);
        RetryPolicy<String> policy = (result, error, attempt, maxAttempts)
                -> "retry".equals(result) && attempt < maxAttempts;

        RetryExecutor<String> executor = new RetryExecutor<>(
                "test", "id", Map.of(),
                new FixedAttemptsStop(2),
                new FixedBackoff(Duration.ofMillis(10)),
                policy,
                listeners,
                Sleeper.system(),
                Clock.system(),
                null, null,
                () -> {
                    taskAttempts.incrementAndGet();
                    return "retry";
                },
                null,
                null, null, null
        );

        String result = executor.run();
        assertEquals("retry", result);
    }

    private RetryExecutor<String> createExecutor(
            StopStrategy stop,
            BackoffStrategy backoff,
            RetryPolicy<String> policy,
            Callable<String> task
    ) {
        return createExecutor(stop, backoff, policy, task, null);
    }

    private RetryExecutor<String> createExecutor(
            StopStrategy stop,
            BackoffStrategy backoff,
            RetryPolicy<String> policy,
            Callable<String> task,
            Function<Throwable, String> fallback
    ) {
        return new RetryExecutor<>(
                "test", "id", Map.of(),
                stop, backoff,
                policy,
                new RetryListeners<>(),
                Sleeper.system(),
                Clock.system(),
                null, null,
                task, fallback,
                null, null, null
        );
    }
}

