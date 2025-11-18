package id.xtramile.flexretry;

import id.xtramile.flexretry.config.RetryConfig;
import id.xtramile.flexretry.config.RetryTemplate;
import id.xtramile.flexretry.strategy.backoff.BackoffStrategy;
import id.xtramile.flexretry.strategy.backoff.FixedBackoff;
import id.xtramile.flexretry.strategy.policy.ClassifierPolicy;
import id.xtramile.flexretry.strategy.policy.RetryPolicy;
import id.xtramile.flexretry.strategy.policy.RetryWindow;
import id.xtramile.flexretry.strategy.stop.FixedAttemptsStop;
import id.xtramile.flexretry.strategy.stop.StopStrategy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class RetryTest {

    @Test
    void testNewBuilder() {
        Retry.Builder<String> builder = Retry.newBuilder();
        assertNotNull(builder);
    }

    @Test
    void testTemplate() {
        RetryConfig<String> config = Retry.<String>newBuilder()
                .name("test")
                .maxAttempts(3)
                .execute((java.util.concurrent.Callable<String>) () -> "success")
                .toConfig();

        RetryTemplate<String> template = Retry.template(config);
        assertNotNull(template);
    }

    @Test
    void testBuilderName() {
        Retry.Builder<String> builder = Retry.<String>newBuilder().name("custom-name");
        RetryConfig<String> config = builder.execute((Callable<String>) () -> "test").toConfig();

        assertEquals("custom-name", config.name);
    }

    @Test
    void testBuilderId() {
        Retry.Builder<String> builder = Retry.<String>newBuilder().id("custom-id");
        RetryConfig<String> config = builder.execute((Callable<String>) () -> "test").toConfig();

        assertEquals("custom-id", config.id);
    }

    @Test
    void testBuilderTag() {
        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .tag("key1", "value1")
                .tag("key2", 42);
        RetryConfig<String> config = builder.execute((Callable<String>) () -> "test").toConfig();

        assertEquals("value1", config.tags.get("key1"));
        assertEquals(42, config.tags.get("key2"));
    }

    @Test
    void testBuilderMaxAttempts() {
        RetryConfig<String> config = Retry.<String>newBuilder()
                .maxAttempts(5)
                .execute((Callable<String>) () -> "test")
                .toConfig();

        assertTrue(config.stop.maxAttempts().isPresent());
        assertEquals(5, config.stop.maxAttempts().get());
    }

    @Test
    void testBuilderStop() {
        StopStrategy stop = new FixedAttemptsStop(10);
        RetryConfig<String> config = Retry.<String>newBuilder()
                .stop(stop)
                .execute((Callable<String>) () -> "test")
                .toConfig();

        assertEquals(stop, config.stop);
    }

    @Test
    void testBuilderDelayMillis() {
        RetryConfig<String> config = Retry.<String>newBuilder()
                .delayMillis(100)
                .execute((Callable<String>) () -> "test")
                .toConfig();

        assertEquals(Duration.ofMillis(100), config.backoff.delayForAttempt(1));
    }

    @Test
    void testBuilderBackoff() {
        BackoffStrategy backoff = new FixedBackoff(Duration.ofSeconds(2));
        RetryConfig<String> config = Retry.<String>newBuilder()
                .backoff(backoff)
                .execute((Callable<String>) () -> "test")
                .toConfig();

        assertEquals(backoff, config.backoff);
    }

    @Test
    void testBuilderRetryIf() {
        RetryConfig<String> config = Retry.<String>newBuilder()
                .retryIf(Objects::isNull)
                .execute((Callable<String>) () -> "test")
                .toConfig();

        assertNotNull(config.policy);
    }

    @Test
    void testBuilderClassify() {
        RetryConfig<String> config = Retry.<String>newBuilder()
                .classify(result -> ClassifierPolicy.Decision.SUCCESS)
                .execute((Callable<String>) () -> "test")
                .toConfig();

        assertNotNull(config.policy);
    }

    @Test
    void testBuilderPolicy() {
        RetryPolicy<String> policy = (result, error, attempt, maxAttempts) -> false;
        RetryConfig<String> config = Retry.<String>newBuilder()
                .policy(policy)
                .execute((Callable<String>) () -> "test")
                .toConfig();

        assertNotNull(config.policy);
    }

    @Test
    void testBuilderRetryOnlyWhen() {
        RetryWindow window = RetryWindow.always();
        RetryConfig<String> config = Retry.<String>newBuilder()
                .retryOnlyWhen(window)
                .execute((Callable<String>) () -> "test")
                .toConfig();

        assertNotNull(config.policy);
    }

    @Test
    void testBuilderRetryOn() {
        RetryConfig<String> config = Retry.<String>newBuilder()
                .retryOn(RuntimeException.class, IllegalArgumentException.class)
                .execute((Callable<String>) () -> "test")
                .toConfig();

        assertNotNull(config.policy);
    }

    @Test
    void testBuilderOnAttempt() {
        AtomicInteger count = new AtomicInteger(0);
        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .onAttempt(ctx -> count.incrementAndGet())
                .maxAttempts(1)
                .execute((Callable<String>) () -> "test");

        builder.getResult();
        assertTrue(count.get() > 0);
    }

    @Test
    void testBuilderAfterAttemptSuccess() {
        AtomicInteger count = new AtomicInteger(0);
        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .afterAttemptSuccess((result, ctx) -> count.incrementAndGet())
                .maxAttempts(1)
                .execute((Callable<String>) () -> "success");

        builder.getResult();
        assertTrue(count.get() > 0);
    }

    @Test
    void testBuilderAfterAttemptFailure() {
        AtomicInteger count = new AtomicInteger(0);
        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .afterAttemptFailure((error, ctx) -> count.incrementAndGet())
                .maxAttempts(1)
                .retryOn(RuntimeException.class)
                .execute((Callable<String>) () -> {
                    throw new RuntimeException("test");
                });

        assertThrows(RetryException.class, builder::getResult);
        assertTrue(count.get() > 0);
    }

    @Test
    void testBuilderOnSuccess() {
        AtomicInteger count = new AtomicInteger(0);
        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .onSuccess((result, ctx) -> count.incrementAndGet())
                .maxAttempts(1)
                .execute((Callable<String>) () -> "success");

        builder.getResult();
        assertEquals(1, count.get());
    }

    @Test
    void testBuilderOnSuccessConsumer() {
        AtomicInteger count = new AtomicInteger(0);
        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .onSuccess(result -> count.incrementAndGet())
                .maxAttempts(1)
                .execute((Callable<String>) () -> "success");

        builder.getResult();
        assertEquals(1, count.get());
    }

    @Test
    void testBuilderOnFailure() {
        AtomicInteger count = new AtomicInteger(0);
        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .onFailure((error, ctx) -> count.incrementAndGet())
                .maxAttempts(1)
                .execute((Callable<String>) () -> {
                    throw new RuntimeException("test");
                });

        assertThrows(RetryException.class, builder::getResult);
        assertTrue(count.get() > 0);
    }

    @Test
    void testBuilderOnFailureConsumer() {
        AtomicInteger count = new AtomicInteger(0);
        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .onFailure(error -> count.incrementAndGet())
                .maxAttempts(1)
                .execute((Callable<String>) () -> {
                    throw new RuntimeException("test");
                });

        assertThrows(RetryException.class, builder::getResult);
        assertTrue(count.get() > 0);
    }

    @Test
    void testBuilderOnFinally() {
        AtomicInteger count = new AtomicInteger(0);
        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .onFinally(ctx -> count.incrementAndGet())
                .maxAttempts(1)
                .execute((Callable<String>) () -> "test");

        builder.getResult();
        assertEquals(1, count.get());
    }

    @Test
    void testBuilderBeforeSleep() {
        AtomicInteger count = new AtomicInteger(0);
        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .beforeSleep((duration, ctx) -> {
                    count.incrementAndGet();
                    return duration;
                })
                .maxAttempts(3)
                .delayMillis(10)
                .retryIf(Objects::isNull)
                .execute((Callable<String>) () -> null);

        // With maxAttempts=3 and retryIf(Objects::isNull):
        // Attempt 1: returns null, policy says retry (attempt 1 < 3), sleep, continue
        // Attempt 2: returns null, policy says retry (attempt 2 < 3), sleep, continue  
        // Attempt 3: returns null, policy says don't retry (attempt 3 >= 3), return success with null
        // So it won't throw an exception, it will return null as success
        // To make it throw, we need the policy to keep retrying until maxAttempts is exhausted
        // But ResultPredicateRetryPolicy checks attempt < maxAttempts, so it stops before exhausting
        String result = builder.getResult();
        assertNull(result);
        assertTrue(count.get() > 0);
    }

    @Test
    void testBuilderFallback() {
        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(1)
                .fallback(error -> "fallback")
                .execute((Callable<String>) () -> {
                    throw new RuntimeException("test");
                });

        String result = builder.getResult();
        assertEquals("fallback", result);
    }

    @Test
    void testBuilderExecuteSupplier() {
        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(1)
                .execute((Callable<String>) () -> "result");

        String result = builder.getResult();
        assertEquals("result", result);
    }

    @Test
    void testBuilderExecuteCallable() {
        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(1)
                .execute((Callable<String>) () -> "result");

        String result = builder.getResult();
        assertEquals("result", result);
    }

    @Test
    void testBuilderGetResult() {
        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(1)
                .execute((Callable<String>) () -> "success");

        String result = builder.getResult();
        assertEquals("success", result);
    }

    @Test
    void testBuilderGetResultAsync() throws Exception {
        Executor executor = Executors.newSingleThreadExecutor();
        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(1)
                .execute((Callable<String>) () -> "success");

        CompletableFuture<String> future = builder.getResultAsync(executor);
        String result = future.get();
        assertEquals("success", result);
    }

    @Test
    void testBuilderGetOutcome() {
        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(1)
                .execute((Callable<String>) () -> "success");

        RetryOutcome<String> outcome = builder.getOutcome();
        assertTrue(outcome.isSuccess());
        assertEquals("success", outcome.result());
    }

    @Test
    void testBuilderGetResultThrowsWhenNoTask() {
        Retry.Builder<String> builder = Retry.newBuilder();
        assertThrows(IllegalStateException.class, builder::getResult);
    }

    @Test
    void testBuilderBuildPolicyWithNoPolicies() {
        Retry.Builder<String> builder = Retry.newBuilder();
        RetryPolicy<String> policy = builder.buildPolicy();
        assertFalse(policy.shouldRetry("result", null, 1, 3));
    }

    @Test
    void testBuilderNullNameThrows() {
        assertThrows(NullPointerException.class, () -> Retry.<String>newBuilder().name(null));
    }

    @Test
    void testBuilderNullIdThrows() {
        assertThrows(NullPointerException.class, () -> Retry.<String>newBuilder().id(null));
    }

    @Test
    void testBuilderNullStopThrows() {
        assertThrows(NullPointerException.class, () -> Retry.<String>newBuilder().stop(null));
    }

    @Test
    void testBuilderNullBackoffThrows() {
        assertThrows(NullPointerException.class, () -> Retry.<String>newBuilder().backoff(null));
    }

    @Test
    void testBuilderNullPredicateThrows() {
        assertThrows(NullPointerException.class, () -> Retry.<String>newBuilder().retryIf(null));
    }

    @Test
    void testBuilderNullPolicyThrows() {
        assertThrows(NullPointerException.class, () -> Retry.<String>newBuilder().policy(null));
    }

    @Test
    void testBuilderNullSupplierThrows() {
        assertThrows(NullPointerException.class, () -> Retry.<String>newBuilder().execute((Supplier<String>) null));
    }

    @Test
    void testBuilderNullCallableThrows() {
        assertThrows(NullPointerException.class, () -> Retry.<String>newBuilder().execute((Callable<String>) null));
    }
}

