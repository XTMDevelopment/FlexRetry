package id.xtramile.flexretry.control;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.config.RetryConfig;
import id.xtramile.flexretry.control.breaker.CircuitBreaker;
import id.xtramile.flexretry.control.breaker.CircuitOpenException;
import id.xtramile.flexretry.control.breaker.FailureAccrualPolicy;
import id.xtramile.flexretry.control.budget.RetryBudget;
import id.xtramile.flexretry.control.budget.TokenBucketRetryBudget;
import id.xtramile.flexretry.control.bulkhead.Bulkhead;
import id.xtramile.flexretry.control.bulkhead.BulkheadFullException;
import id.xtramile.flexretry.control.cache.ResultCache;
import id.xtramile.flexretry.control.concurrency.AimdConcurrencyLimiter;
import id.xtramile.flexretry.control.concurrency.ConcurrencyLimitedException;
import id.xtramile.flexretry.control.concurrency.ConcurrencyLimiter;
import id.xtramile.flexretry.control.health.HealthProbe;
import id.xtramile.flexretry.control.ratelimit.RateLimitExceededException;
import id.xtramile.flexretry.control.ratelimit.RateLimiter;
import id.xtramile.flexretry.control.ratelimit.TokenBucketRateLimiter;
import id.xtramile.flexretry.control.sf.SingleFlight;
import id.xtramile.flexretry.control.tuning.DynamicTuning;
import id.xtramile.flexretry.control.tuning.MutableTuning;
import id.xtramile.flexretry.control.tuning.RetrySwitch;
import id.xtramile.flexretry.strategy.policy.RetryPolicy;
import id.xtramile.flexretry.strategy.stop.StopStrategy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class RetryControlsTest {

    @Test
    void testWithBudget_BasePolicyReturnsFalse() {
        RetryBudget budget = new TokenBucketRetryBudget(10.0, 50.0);
        RetryPolicy<String> base = (result, error, attempt, maxAttempts) -> false;
        
        RetryPolicy<String> policy = RetryControls.withBudget(base, budget);
        
        assertFalse(policy.shouldRetry("result", null, 1, 3));
    }

    @Test
    void testWithBudget_BudgetAllows() {
        RetryBudget budget = new TokenBucketRetryBudget(10.0, 50.0);
        RetryPolicy<String> base = (result, error, attempt, maxAttempts) -> true;
        
        RetryPolicy<String> policy = RetryControls.withBudget(base, budget);
        
        assertTrue(policy.shouldRetry("result", null, 1, 3));
    }

    @Test
    void testWithBudget_BudgetDenies() {
        RetryBudget budget = new TokenBucketRetryBudget(1.0, 1.0);
        budget.tryAcquire(); // Exhaust it
        RetryPolicy<String> base = (result, error, attempt, maxAttempts) -> true;
        
        RetryPolicy<String> policy = RetryControls.withBudget(base, budget);

        assertFalse(policy.shouldRetry("result", null, 1, 3));
    }

    @Test
    void testWithBudget_NullBase() {
        RetryBudget budget = new TokenBucketRetryBudget(10.0, 50.0);
        
        assertThrows(NullPointerException.class,
                () -> RetryControls.withBudget(null, budget));
    }

    @Test
    void testWithBudget_NullBudget() {
        RetryPolicy<String> base = (result, error, attempt, maxAttempts) -> true;
        
        assertThrows(NullPointerException.class,
                () -> RetryControls.withBudget(base, null));
    }

    @Test
    void testBudget() {
        RetryBudget budget = new TokenBucketRetryBudget(10.0, 50.0);
        Retry.Builder<String> builder = Retry.newBuilder();
        
        Retry.Builder<String> result = RetryControls.budget(builder, budget);
        
        assertSame(builder, result);
    }

    @Test
    void testBulkhead_Supplier_Success() {
        Bulkhead bulkhead = new Bulkhead(5);
        AtomicInteger callCount = new AtomicInteger(0);
        
        Supplier<String> wrapped = RetryControls.bulkhead(bulkhead, (Supplier<String>) () -> {
            callCount.incrementAndGet();
            return "success";
        });
        
        String result = wrapped.get();
        assertEquals("success", result);
        assertEquals(1, callCount.get());
    }

    @Test
    void testBulkhead_Supplier_Full() {
        Bulkhead bulkhead = new Bulkhead(1);
        bulkhead.tryAcquire();
        
        Supplier<String> wrapped = RetryControls.bulkhead(bulkhead, (Supplier<String>) () -> "success");
        
        assertThrows(BulkheadFullException.class, wrapped::get);
    }

    @Test
    void testBulkhead_Callable_Success() throws Exception {
        Bulkhead bulkhead = new Bulkhead(5);
        
        Callable<String> wrapped = RetryControls.bulkhead(bulkhead, (Callable<String>) () -> "success");
        
        String result = wrapped.call();
        assertEquals("success", result);
    }

    @Test
    void testBulkhead_Callable_Full() {
        Bulkhead bulkhead = new Bulkhead(1);
        bulkhead.tryAcquire();
        
        Callable<String> wrapped = RetryControls.bulkhead(bulkhead, (Callable<String>) () -> "success");
        
        assertThrows(BulkheadFullException.class, wrapped::call);
    }

    @Test
    void testBulkhead_ReleasesOnException() {
        Bulkhead bulkhead = new Bulkhead(1);
        
        Supplier<String> wrapped = RetryControls.bulkhead(bulkhead, (Supplier<String>) () -> {
            throw new RuntimeException("test");
        });
        
        assertThrows(RuntimeException.class, wrapped::get);

        assertTrue(bulkhead.tryAcquire());
    }

    @Test
    void testSingleFlight() {
        SingleFlight<String> sf = new SingleFlight<>();
        RetryConfig<String> config = Retry.<String>newBuilder()
            .name("test")
            .id("test-id")
            .execute((Callable<String>) () -> "dummy")
            .toConfig();
        
        AtomicInteger callCount = new AtomicInteger(0);
        Supplier<String> wrapped = RetryControls.singleFlight(
            sf,
            config,
            ctx -> ctx.id() + ":" + ctx.name(),
            () -> {
                callCount.incrementAndGet();
                return "result";
            }
        );
        
        String result = wrapped.get();
        assertEquals("result", result);
        assertEquals(1, callCount.get());
    }

    @Test
    void testCache() {
        ResultCache<String, String> cache = new ResultCache<>() {
            private final Map<String, String> map = new ConcurrentHashMap<>();

            @Override
            public Optional<String> get(String key) {
                return Optional.ofNullable(map.get(key));
            }

            @Override
            public void put(String key, String value, Duration ttl) {
                map.put(key, value);
            }
        };
        
        Retry.Builder<String> builder = Retry.newBuilder();
        
        RetryControls.cache(
            builder,
            cache,
            ctx -> ctx.id() + ":" + ctx.name(),
            Duration.ofSeconds(1)
        );

        assertNotNull(builder);
    }

    @Test
    void testCachingSupplier() {
        ResultCache<String, String> cache = new ResultCache<>() {
            private final Map<String, String> map = new ConcurrentHashMap<>();

            @Override
            public Optional<String> get(String key) {
                return Optional.ofNullable(map.get(key));
            }

            @Override
            public void put(String key, String value, Duration ttl) {
                map.put(key, value);
            }
        };
        
        RetryConfig<String> config = Retry.<String>newBuilder()
            .name("test")
            .id("test-id")
            .execute((Callable<String>) () -> "dummy")
            .toConfig();
        
        AtomicInteger callCount = new AtomicInteger(0);
        Supplier<String> wrapped = RetryControls.cachingSupplier(
            cache,
            config,
            ctx -> ctx.id() + ":" + ctx.name(),
            Duration.ofSeconds(1),
            () -> {
                callCount.incrementAndGet();
                return "result";
            }
        );
        
        String result1 = wrapped.get();
        assertEquals("result", result1);
        assertEquals(1, callCount.get());
        
        String result2 = wrapped.get();
        assertEquals("result", result2);
        assertEquals(1, callCount.get());
    }

    @Test
    void testSwitchStop() {
        RetrySwitch retrySwitch = new RetrySwitch();
        
        StopStrategy strategy = RetryControls.switchStop(retrySwitch);
        
        assertFalse(strategy.shouldStop(1, 0, 0, Duration.ZERO));
        
        retrySwitch.setOn(false);
        assertTrue(strategy.shouldStop(1, 0, 0, Duration.ZERO));
    }

    @Test
    void testTunedStop() {
        MutableTuning tuning = new MutableTuning();
        tuning.setMaxAttempts(5);
        tuning.setMaxElapsed(Duration.ofSeconds(30));
        
        StopStrategy strategy = RetryControls.tunedStop(tuning);
        
        assertNotNull(strategy);
    }

    @Test
    void testApplyDynamicTuning() {
        HealthProbe health = () -> HealthProbe.State.UP;
        DynamicTuning tuning = (state, builder) -> {
            if (state == HealthProbe.State.DOWN) {
                builder.maxAttempts(1);
            }
        };
        MutableTuning mutable = new MutableTuning();
        
        Retry.Builder<String> builder = Retry.newBuilder();
        Retry.Builder<String> result = RetryControls.applyDynamicTuning(builder, health, tuning, mutable);
        
        assertSame(builder, result);
    }

    @Test
    void testApplyDynamicTuning_NullParameters() {
        Retry.Builder<String> builder = Retry.newBuilder();
        Retry.Builder<String> result = RetryControls.applyDynamicTuning(builder, null, null, null);
        
        assertSame(builder, result);
    }

    @Test
    void testRateLimited_Success() {
        RateLimiter limiter = new TokenBucketRateLimiter(10, 10);
        
        Supplier<String> wrapped = RetryControls.rateLimited(() -> "success", limiter);
        
        String result = wrapped.get();
        assertEquals("success", result);
    }

    @Test
    void testRateLimited_Exceeded() {
        RateLimiter limiter = new TokenBucketRateLimiter(1, 1);
        limiter.tryAcquire(); // Exhaust it
        
        Supplier<String> wrapped = RetryControls.rateLimited(() -> "success", limiter);
        
        assertThrows(RateLimitExceededException.class, wrapped::get);
    }

    @Test
    void testConcurrencyLimited_Success() {
        ConcurrencyLimiter limiter = new AimdConcurrencyLimiter(5, 10);
        
        Supplier<String> wrapped = RetryControls.concurrencyLimited(() -> "success", limiter);
        
        String result = wrapped.get();
        assertEquals("success", result);
    }

    @Test
    void testConcurrencyLimited_Limited() {
        ConcurrencyLimiter limiter = new AimdConcurrencyLimiter(1, 10);
        limiter.tryAcquire();
        
        Supplier<String> wrapped = RetryControls.concurrencyLimited(() -> "success", limiter);
        
        assertThrows(ConcurrencyLimitedException.class, wrapped::get);
    }

    @Test
    void testConcurrencyLimited_OnSuccess() {
        ConcurrencyLimiter limiter = new AimdConcurrencyLimiter(1, 10);
        
        Supplier<String> wrapped = RetryControls.concurrencyLimited(() -> "success", limiter);
        
        String result = wrapped.get();
        assertEquals("success", result);
        assertTrue(limiter.tryAcquire());
    }

    @Test
    void testConcurrencyLimited_OnDropped() {
        ConcurrencyLimiter limiter = new AimdConcurrencyLimiter(1, 10);
        
        Supplier<String> wrapped = RetryControls.concurrencyLimited(() -> {
            throw new RuntimeException("test");
        }, limiter);
        
        assertThrows(RuntimeException.class, wrapped::get);
        assertTrue(limiter.tryAcquire());
    }

    @Test
    void testCircuitBreak_Success() {
        FailureAccrualPolicy policy = createSimplePolicy(10);
        CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofSeconds(1));
        
        Supplier<String> wrapped = RetryControls.circuitBreak(breaker, () -> "success");
        
        String result = wrapped.get();
        assertEquals("success", result);
    }

    @Test
    void testCircuitBreak_Open() {
        FailureAccrualPolicy policy = createSimplePolicy(2);
        CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofSeconds(1));

        breaker.onFailure();
        breaker.onFailure();
        
        Supplier<String> wrapped = RetryControls.circuitBreak(breaker, () -> "success");
        
        assertThrows(CircuitOpenException.class, wrapped::get);
    }

    @Test
    void testCircuitBreak_OnSuccess() {
        FailureAccrualPolicy policy = createSimplePolicy(10);
        CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofSeconds(1));
        
        Supplier<String> wrapped = RetryControls.circuitBreak(breaker, () -> "success");
        
        String result = wrapped.get();
        assertEquals("success", result);
    }

    @Test
    void testCircuitBreak_OnFailure() {
        FailureAccrualPolicy policy = createSimplePolicy(10);
        CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofSeconds(1));
        
        Supplier<String> wrapped = RetryControls.circuitBreak(breaker, () -> {
            throw new RuntimeException("test");
        });
        
        assertThrows(RuntimeException.class, wrapped::get);
    }

    @Test
    void testHedged_PrimarySucceeds() {
        AtomicInteger primaryCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);
        
        Supplier<String> primary = () -> {
            primaryCount.incrementAndGet();
            return "primary";
        };

        Supplier<String> duplicate = () -> {
            duplicateCount.incrementAndGet();
            return "duplicate";
        };
        
        Supplier<String> hedged = RetryControls.hedged(primary, duplicate, 100);
        
        String result = hedged.get();
        assertEquals("primary", result);
        assertEquals(1, primaryCount.get());
        assertEquals(0, duplicateCount.get());
    }

    @Test
    void testHedged_PrimaryTimeout() {
        AtomicInteger primaryCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);
        
        Supplier<String> primary = () -> {
            primaryCount.incrementAndGet();

            try {
                Thread.sleep(200);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return "primary";
        };

        Supplier<String> duplicate = () -> {
            duplicateCount.incrementAndGet();
            return "duplicate";
        };
        
        Supplier<String> hedged = RetryControls.hedged(primary, duplicate, 50);
        
        String result = hedged.get();
        assertTrue(result.equals("primary") || result.equals("duplicate"));
    }

    private FailureAccrualPolicy createSimplePolicy(int threshold) {
        final int finalThreshold = threshold;
        return new FailureAccrualPolicy() {
            private int failures = 0;

            @Override
            public boolean recordSuccess() {
                return true;
            }

            @Override
            public boolean recordFailure() {
                failures++;
                return failures >= finalThreshold;
            }

            @Override
            public boolean isTripped() {
                return failures >= finalThreshold;
            }

            @Override
            public void reset() {
                failures = 0;
            }
        };
    }
}

