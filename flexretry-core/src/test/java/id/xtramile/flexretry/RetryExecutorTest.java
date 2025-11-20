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

        RetryListeners<String> listeners = createListeners(onAttempt, onSuccess, onFailure, onFinally);
        RetryPolicy<String> policy = (result, error, attempt, maxAttempts) -> false;
        RetryExecutor<String> executor = createExecutorWithListeners(
                new FixedAttemptsStop(1),
                new FixedBackoff(Duration.ZERO),
                policy,
                listeners,
                () -> "success"
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

        AttemptLifecycle<String> lifecycle = createLifecycle(beforeAttempt, afterSuccess, afterFailure);
        RetryPolicy<String> policy = (result, error, attempt, maxAttempts) -> false;
        RetryExecutor<String> executor = createExecutorWithLifecycle(
                new FixedAttemptsStop(1),
                new FixedBackoff(Duration.ZERO),
                policy,
                lifecycle,
                () -> "success"
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

        RetryExecutor<String> executor = createExecutorWithBackoffRouter(
                new FixedAttemptsStop(2),
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> error != null && attempt < maxAttempts,
                router,
                () -> {
                    throw new RuntimeException("error");
                }
        );

        assertThrows(RetryException.class, executor::run);
    }

    @Test
    void testAttemptTimeout() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            long startTime = System.currentTimeMillis();
            
            RetryExecutor<String> executor = createExecutorWithTimeout(
                    new FixedAttemptsStop(1),
                    new FixedBackoff(Duration.ZERO),
                    (result, error, attempt, maxAttempts) -> false,
                    Duration.ofMillis(100),
                    executorService,
                    () -> {
                        long attemptStart = System.currentTimeMillis();
                        Thread.sleep(300);
                        long attemptElapsed = System.currentTimeMillis() - attemptStart;
                        System.out.println("testAttemptTimeout: Attempt took " + attemptElapsed + "ms");
                        return "success";
                    }
            );

            assertThrows(RetryException.class, () -> {
                try {
                    executor.run();
                } finally {
                    long totalElapsed = System.currentTimeMillis() - startTime;
                    System.out.println("testAttemptTimeout: Total execution took " + totalElapsed + "ms");
                }
            });
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    void testAttemptTimeoutStrategy() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            long startTime = System.currentTimeMillis();
            
            AttemptTimeoutStrategy timeoutStrategy = new FixedTimeout(Duration.ofMillis(100));
            RetryExecutor<String> executor = createExecutorWithTimeoutStrategy(
                    new FixedAttemptsStop(1),
                    new FixedBackoff(Duration.ZERO),
                    (result, error, attempt, maxAttempts) -> false,
                    executorService,
                    timeoutStrategy,
                    () -> {
                        long attemptStart = System.currentTimeMillis();
                        Thread.sleep(300);
                        long attemptElapsed = System.currentTimeMillis() - attemptStart;
                        System.out.println("testAttemptTimeoutStrategy: Attempt took " + attemptElapsed + "ms");
                        return "success";
                    }
            );

            assertThrows(RetryException.class, () -> {
                try {
                    executor.run();
                } finally {
                    long totalElapsed = System.currentTimeMillis() - startTime;
                    System.out.println("testAttemptTimeoutStrategy: Total execution took " + totalElapsed + "ms");
                }
            });
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
        StopStrategy stop = createStopStrategy();
        RetryExecutor<String> executor = createExecutorWithClock(
                stop,
                new FixedBackoff(Duration.ZERO),
                (result, error, attempt, maxAttempts) -> true,
                clock,
                () -> "success"
        );

        RetryOutcome<String> outcome = executor.runWithOutcome();
        assertFalse(outcome.isSuccess());
    }

    @Test
    void testBeforeSleep() {
        AtomicInteger sleepCount = new AtomicInteger(0);
        RetryListeners<String> listeners = createListenersWithBeforeSleep(sleepCount);
        AtomicInteger taskAttempts = new AtomicInteger(0);
        RetryPolicy<String> policy = (result, error, attempt, maxAttempts)
                -> "retry".equals(result) && attempt < maxAttempts;

        RetryExecutor<String> executor = createExecutorWithListeners(
                new FixedAttemptsStop(2),
                new FixedBackoff(Duration.ofMillis(10)),
                policy,
                listeners,
                () -> {
                    taskAttempts.incrementAndGet();
                    return "retry";
                }
        );

        String result = executor.run();
        assertEquals("retry", result);
        assertTrue(sleepCount.get() > 0);
    }

    @Test
    void testNullSafe() {
        RetryListeners<String> listeners = createListenersWithFailingBeforeSleep();
        AtomicInteger taskAttempts = new AtomicInteger(0);
        RetryPolicy<String> policy = (result, error, attempt, maxAttempts)
                -> "retry".equals(result) && attempt < maxAttempts;

        RetryExecutor<String> executor = createExecutorWithListeners(
                new FixedAttemptsStop(2),
                new FixedBackoff(Duration.ofMillis(10)),
                policy,
                listeners,
                () -> {
                    taskAttempts.incrementAndGet();
                    return "retry";
                }
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

    private RetryListeners<String> createListeners(
            AtomicInteger onAttempt, AtomicInteger onSuccess,
            AtomicInteger onFailure, AtomicInteger onFinally) {
        RetryListeners<String> listeners = new RetryListeners<>();

        listeners.onAttempt = ctx -> onAttempt.incrementAndGet();
        listeners.onSuccess = (result, ctx) -> onSuccess.incrementAndGet();
        listeners.onFailure = (error, ctx) -> onFailure.incrementAndGet();
        listeners.onFinally = ctx -> onFinally.incrementAndGet();

        return listeners;
    }

    private RetryListeners<String> createListenersWithBeforeSleep(AtomicInteger sleepCount) {
        RetryListeners<String> listeners = new RetryListeners<>();
        listeners.beforeSleep = (duration, ctx) -> {
            sleepCount.incrementAndGet();
            return duration;
        };

        return listeners;
    }

    private RetryListeners<String> createListenersWithFailingBeforeSleep() {
        RetryListeners<String> listeners = new RetryListeners<>();
        listeners.beforeSleep = (duration, ctx) -> {
            throw new RuntimeException("error in beforeSleep");
        };

        return listeners;
    }

    private AttemptLifecycle<String> createLifecycle(
            AtomicInteger beforeAttempt, AtomicInteger afterSuccess, AtomicInteger afterFailure) {
        return new AttemptLifecycle<>() {
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
    }

    private RetryExecutor<String> createExecutorWithListeners(
            StopStrategy stop, BackoffStrategy backoff, RetryPolicy<String> policy,
            RetryListeners<String> listeners, Callable<String> task) {

        return new RetryExecutor<>(
                "test", "id", Map.of(),
                stop, backoff,
                policy,
                listeners,
                Sleeper.system(),
                Clock.system(),
                null, null,
                task,
                null,
                null, null, null
        );
    }

    private RetryExecutor<String> createExecutorWithLifecycle(
            StopStrategy stop, BackoffStrategy backoff, RetryPolicy<String> policy,
            AttemptLifecycle<String> lifecycle, Callable<String> task) {

        return new RetryExecutor<>(
                "test", "id", Map.of(),
                stop, backoff,
                policy,
                new RetryListeners<>(),
                Sleeper.system(),
                Clock.system(),
                null, null,
                task,
                null,
                null, lifecycle, null
        );
    }

    private RetryExecutor<String> createExecutorWithBackoffRouter(
            StopStrategy stop, BackoffStrategy backoff, RetryPolicy<String> policy,
            BackoffRouter router, Callable<String> task) {

        return new RetryExecutor<>(
                "test", "id", Map.of(),
                stop, backoff,
                policy,
                new RetryListeners<>(),
                Sleeper.system(),
                Clock.system(),
                null, null,
                task,
                null,
                router, null, null
        );
    }

    private RetryExecutor<String> createExecutorWithTimeout(
            StopStrategy stop, BackoffStrategy backoff, RetryPolicy<String> policy,
            Duration attemptTimeout, ExecutorService attemptExecutor, Callable<String> task) {

        return new RetryExecutor<>(
                "test", "id", Map.of(),
                stop, backoff,
                policy,
                new RetryListeners<>(),
                Sleeper.system(),
                Clock.system(),
                attemptTimeout, attemptExecutor,
                task,
                null,
                null, null, null
        );
    }

    private RetryExecutor<String> createExecutorWithTimeoutStrategy(
            StopStrategy stop, BackoffStrategy backoff, RetryPolicy<String> policy,
            ExecutorService attemptExecutor, AttemptTimeoutStrategy attemptTimeouts, Callable<String> task) {

        return new RetryExecutor<>(
                "test", "id", Map.of(),
                stop, backoff,
                policy,
                new RetryListeners<>(),
                Sleeper.system(),
                Clock.system(),
                null, attemptExecutor,
                task,
                null,
                null, null, attemptTimeouts
        );
    }

    private RetryExecutor<String> createExecutorWithClock(
            StopStrategy stop, BackoffStrategy backoff, RetryPolicy<String> policy,
            Clock clock, Callable<String> task) {

        return new RetryExecutor<>(
                "test", "id", Map.of(),
                stop, backoff,
                policy,
                new RetryListeners<>(),
                Sleeper.system(),
                clock,
                null, null,
                task,
                null,
                null, null, null
        );
    }

    private StopStrategy createStopStrategy() {
        return (attempt, startNanos, nowNanos, nextDelay) -> attempt > 1;
    }
}

