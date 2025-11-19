package id.xtramile.flexretry.control.integration;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.control.RetryControls;
import id.xtramile.flexretry.control.budget.TokenBucketRetryBudget;
import id.xtramile.flexretry.control.breaker.CircuitBreaker;
import id.xtramile.flexretry.control.breaker.CircuitOpenException;
import id.xtramile.flexretry.control.breaker.FailureAccrualPolicy;
import id.xtramile.flexretry.control.bulkhead.Bulkhead;
import id.xtramile.flexretry.control.bulkhead.BulkheadFullException;
import id.xtramile.flexretry.control.cache.ResultCache;
import id.xtramile.flexretry.control.concurrency.AimdConcurrencyLimiter;
import id.xtramile.flexretry.control.concurrency.ConcurrencyLimitedException;
import id.xtramile.flexretry.control.ratelimit.RateLimitExceededException;
import id.xtramile.flexretry.control.ratelimit.TokenBucketRateLimiter;
import id.xtramile.flexretry.control.sf.SingleFlight;
import id.xtramile.flexretry.control.tuning.MutableTuning;
import id.xtramile.flexretry.control.tuning.RetrySwitch;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RetryControls with RetryExecutors
 */
class RetryControlsIntegrationTest {

    @Test
    void testRetryWithBudget() {
        TokenBucketRetryBudget budget = new TokenBucketRetryBudget(10.0, 50.0);
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        String result = Retry.<String>newBuilder()
            .maxAttempts(5)
            .policy(RetryControls.withBudget(
                (r, e, a, m) -> e != null,
                budget
            ))
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
    }

    @Test
    void testRetryWithBudget_Exhausted() {
        TokenBucketRetryBudget budget = new TokenBucketRetryBudget(2.0, 2.0);

        budget.tryAcquire();
        budget.tryAcquire();
        
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        assertThrows(RuntimeException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(5)
                    .policy(RetryControls.withBudget(
                        (r, e, a, m) -> e != null,
                        budget
                    ))
                    .execute((Callable<String>) () -> {
                        attemptCount.incrementAndGet();
                        throw new RuntimeException("retry");
                    })
                    .getResult());

        assertTrue(attemptCount.get() > 0);
    }

    @Test
    void testRetryWithBulkhead() {
        Bulkhead bulkhead = new Bulkhead(2);
        AtomicInteger successCount = new AtomicInteger(0);
        
        String result = Retry.<String>newBuilder()
            .maxAttempts(3)
            .execute(RetryControls.bulkhead(bulkhead, (Supplier<String>) () -> {
                successCount.incrementAndGet();
                return "success";
            }))
            .getResult();
        
        assertEquals("success", result);
        assertEquals(1, successCount.get());
    }

    @Test
    void testRetryWithBulkhead_Full() {
        Bulkhead bulkhead = new Bulkhead(1);
        bulkhead.tryAcquire();
        
        id.xtramile.flexretry.RetryException exception = assertThrows(id.xtramile.flexretry.RetryException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(3)
                    .execute(RetryControls.bulkhead(bulkhead, (Supplier<String>) () -> "success"))
                    .getResult());
        
        assertInstanceOf(BulkheadFullException.class, exception.getCause());
    }

    @Test
    void testRetryWithCircuitBreaker() {
        FailureAccrualPolicy policy = createSimplePolicy(3);
        CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofSeconds(1));
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        String result = Retry.<String>newBuilder()
            .maxAttempts(5)
            .execute(RetryControls.circuitBreak(breaker, () -> {
                attemptCount.incrementAndGet();
                return "success";
            }))
            .getResult();
        
        assertEquals("success", result);
        assertEquals(1, attemptCount.get());
    }

    @Test
    void testRetryWithCircuitBreaker_Open() {
        FailureAccrualPolicy policy = createSimplePolicy(2);
        CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofSeconds(1));
        
        breaker.onFailure();
        breaker.onFailure();
        
        id.xtramile.flexretry.RetryException exception = assertThrows(id.xtramile.flexretry.RetryException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(5)
                    .execute(RetryControls.circuitBreak(breaker, (Supplier<String>) () -> "success"))
                    .getResult());
        
        assertInstanceOf(CircuitOpenException.class, exception.getCause());
    }

    @Test
    void testRetryWithRateLimiter() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 10);
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        String result = Retry.<String>newBuilder()
            .maxAttempts(3)
            .execute(RetryControls.rateLimited(() -> {
                attemptCount.incrementAndGet();
                return "success";
            }, limiter))
            .getResult();
        
        assertEquals("success", result);
        assertEquals(1, attemptCount.get());
    }

    @Test
    void testRetryWithRateLimiter_Exceeded() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1);
        limiter.tryAcquire(); // Exhaust it
        
        id.xtramile.flexretry.RetryException exception = assertThrows(id.xtramile.flexretry.RetryException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(3)
                    .execute(RetryControls.rateLimited((Supplier<String>) () -> "success", limiter))
                    .getResult());
        
        assertInstanceOf(RateLimitExceededException.class, exception.getCause());
    }

    @Test
    void testRetryWithConcurrencyLimiter() {
        AimdConcurrencyLimiter limiter = new AimdConcurrencyLimiter(5, 10);
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        String result = Retry.<String>newBuilder()
            .maxAttempts(3)
            .execute(RetryControls.concurrencyLimited(() -> {
                attemptCount.incrementAndGet();
                return "success";
            }, limiter))
            .getResult();
        
        assertEquals("success", result);
        assertEquals(1, attemptCount.get());
    }

    @Test
    void testRetryWithConcurrencyLimiter_Limited() {
        AimdConcurrencyLimiter limiter = new AimdConcurrencyLimiter(1, 10);
        limiter.tryAcquire();
        
        id.xtramile.flexretry.RetryException exception = assertThrows(id.xtramile.flexretry.RetryException.class,
                () -> Retry.<String>newBuilder()
                    .maxAttempts(3)
                    .execute(RetryControls.concurrencyLimited((Supplier<String>) () -> "success", limiter))
                    .getResult());
        
        assertInstanceOf(ConcurrencyLimitedException.class, exception.getCause());
    }

    @Test
    void testRetryWithSingleFlight() {
        SingleFlight<String> sf = new SingleFlight<>();
        AtomicInteger callCount = new AtomicInteger(0);
        
        String result = Retry.<String>newBuilder()
            .name("test")
            .id("test-id")
            .maxAttempts(3)
            .execute(RetryControls.singleFlight(
                sf,
                Retry.<String>newBuilder().name("test").id("test-id").execute((Callable<String>) () -> "dummy").toConfig(),
                ctx -> ctx.id() + ":" + ctx.name(),
                () -> {
                    callCount.incrementAndGet();
                    return "success";
                }
            ))
            .getResult();
        
        assertEquals("success", result);
        assertEquals(1, callCount.get());
    }

    @Test
    void testRetryWithCaching() {
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
        
        AtomicInteger callCount = new AtomicInteger(0);
        
        String result1 = RetryControls.cachingSupplier(
            cache,
            Retry.<String>newBuilder().name("test").id("test-id").execute((Callable<String>) () -> "dummy").toConfig(),
            ctx -> ctx.id() + ":" + ctx.name(),
            Duration.ofSeconds(1),
            () -> {
                callCount.incrementAndGet();
                return "success";
            }
        ).get();
        
        assertEquals("success", result1);
        assertEquals(1, callCount.get());
        
        String result2 = RetryControls.cachingSupplier(
            cache,
            Retry.<String>newBuilder().name("test").id("test-id").execute((Callable<String>) () -> "dummy").toConfig(),
            ctx -> ctx.id() + ":" + ctx.name(),
            Duration.ofSeconds(1),
            () -> {
                callCount.incrementAndGet();
                return "success";
            }
        ).get();
        
        assertEquals("success", result2);
        assertEquals(1, callCount.get());
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
        id.xtramile.flexretry.RetryException exception = assertThrows(id.xtramile.flexretry.RetryException.class,
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
    void testRetryWithCombinedControls() {
        Bulkhead bulkhead = new Bulkhead(5);
        TokenBucketRetryBudget budget = new TokenBucketRetryBudget(10.0, 50.0);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 10);
        
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        String result = Retry.<String>newBuilder()
            .maxAttempts(5)
            .policy(RetryControls.withBudget(
                (r, e, a, m) -> e != null,
                budget
            ))
            .execute(RetryControls.rateLimited(
                RetryControls.bulkhead(bulkhead, (Supplier<String>) () -> {
                    attemptCount.incrementAndGet();
                    if (attemptCount.get() < 2) {
                        throw new RuntimeException("retry");
                    }
                    return "success";
                }),
                limiter
            ))
            .getResult();
        
        assertEquals("success", result);
        assertTrue(attemptCount.get() >= 2);
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

