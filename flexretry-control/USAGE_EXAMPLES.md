# RetryControls Integration Patterns

The `RetryControls` methods integrate with the retry mechanism in three main ways:

## Pattern 1: Builder Integration (Modifies Retry.Builder)

These methods modify the `Retry.Builder` directly and return it for fluent chaining:

### Budget Control
```java
RetryBudget budget = new TokenBucketRetryBudget(10.0, 50.0);

Retry.Builder<String> builder = Retry.newBuilder<String>()
    .name("apiCall")
    .maxAttempts(5);

// Integrate budget into the retry policy
RetryControls.budget(builder, budget);

// Now retries are limited by the budget
String result = builder
    .execute(() -> apiCall())
    .getResult();
```

### Caching
```java
ResultCache<String, String> cache = new InMemoryResultCache<>();

Retry.Builder<String> builder = Retry.newBuilder<String>()
    .name("cachedCall");

// Add caching to the builder
RetryControls.cache(
    builder,
    cache,
    ctx -> ctx.id() + ":" + ctx.name(),  // cache key from context
    Duration.ofMinutes(5)
);

String result = builder
    .execute(() -> expensiveOperation())
    .getResult();
```

### Dynamic Tuning
```java
HealthProbe health = new MyHealthProbe();
DynamicTuning tuning = new MyDynamicTuning();
MutableTuning mutable = new MutableTuning();

Retry.Builder<String> builder = Retry.newBuilder<String>();

// Apply dynamic tuning based on health
RetryControls.applyDynamicTuning(builder, health, tuning, mutable);

String result = builder
    .execute(() -> operation())
    .getResult();
```

## Pattern 2: Task Wrapping (Wraps Supplier/Callable)

These methods wrap your task with control logic, returning a new `Supplier` or `Callable`:

### Bulkhead
```java
Bulkhead bulkhead = new Bulkhead(10); // max 10 concurrent

Supplier<String> originalTask = () -> apiCall();

// Wrap with bulkhead - throws BulkheadFullException if full
Supplier<String> protectedTask = RetryControls.bulkhead(bulkhead, originalTask);

// Use in retry
String result = Retry.newBuilder<String>()
    .execute(protectedTask)  // Now protected by bulkhead
    .getResult();
```

### Rate Limiting
```java
RateLimiter limiter = new TokenBucketRateLimiter(100, 10); // 100 capacity, 10/sec

Supplier<String> originalTask = () -> apiCall();

// Wrap with rate limiter - throws RateLimitExceededException if exceeded
Supplier<String> rateLimitedTask = RetryControls.rateLimited(originalTask, limiter);

String result = Retry.newBuilder<String>()
    .execute(rateLimitedTask)
    .getResult();
```

### Circuit Breaker
```java
CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofSeconds(30));

Supplier<String> originalTask = () -> apiCall();

// Wrap with circuit breaker - throws CircuitOpenException if open
Supplier<String> protectedTask = RetryControls.circuitBreak(breaker, originalTask);

String result = Retry.newBuilder<String>()
    .execute(protectedTask)
    .getResult();
```

### Concurrency Limiting
```java
ConcurrencyLimiter limiter = new AimdConcurrencyLimiter(10, 100);

Supplier<String> originalTask = () -> apiCall();

// Wrap with concurrency limiter - throws ConcurrencyLimitedException if limited
Supplier<String> limitedTask = RetryControls.concurrencyLimited(originalTask, limiter);

String result = Retry.newBuilder<String>()
    .execute(limitedTask)
    .getResult();
```

### Single Flight (Deduplication)
```java
SingleFlight<String> sf = new SingleFlight<>();
RetryConfig<String> config = Retry.newBuilder<String>()
    .name("dedupeCall")
    .id("call-123")
    .toConfig();

Supplier<String> originalTask = () -> expensiveOperation();

// Wrap with single-flight - deduplicates concurrent calls with same key
Supplier<String> dedupeTask = RetryControls.singleFlight(
    sf,
    config,
    ctx -> ctx.id() + ":" + ctx.name(),  // deduplication key
    originalTask
);

String result = Retry.newBuilder<String>()
    .execute(dedupeTask)
    .getResult();
```

### Caching Supplier
```java
ResultCache<String, String> cache = new InMemoryResultCache<>();
RetryConfig<String> config = Retry.newBuilder<String>()
    .name("cachedCall")
    .id("call-456")
    .toConfig();

Supplier<String> originalTask = () -> expensiveOperation();

// Wrap with caching - uses actual config identity for key
Supplier<String> cachedTask = RetryControls.cachingSupplier(
    cache,
    config,
    ctx -> ctx.id() + ":" + ctx.name(),  // cache key from config
    Duration.ofMinutes(5),
    originalTask
);

String result = cachedTask.get();  // Can use directly or in retry
```

### Hedging
```java
Supplier<String> primaryTask = () -> callPrimaryService();
Supplier<String> duplicateTask = () -> callDuplicateService();

// Wrap with hedging - sends duplicate request after delay
Supplier<String> hedgedTask = RetryControls.hedged(
    primaryTask,
    duplicateTask,
    100  // hedge delay in milliseconds
);

String result = Retry.newBuilder<String>()
    .execute(hedgedTask)
    .getResult();
```

## Pattern 3: Strategy Creation (Returns StopStrategy)

These methods create `StopStrategy` instances that can be used with `Retry.Builder`:

### Retry Switch
```java
RetrySwitch switch = new RetrySwitch();

Retry.Builder<String> builder = Retry.newBuilder<String>()
    .stop(RetryControls.switchStop(switch))  // Stops retrying if switch is off
    .execute(() -> operation());

// Can toggle retries at runtime
switch.setOn(false);  // Retries will stop
```

### Tuned Stop
```java
MutableTuning tuning = new MutableTuning();
tuning.setMaxAttempts(5);
tuning.setMaxElapsed(Duration.ofSeconds(30));

Retry.Builder<String> builder = Retry.newBuilder<String>()
    .stop(RetryControls.tunedStop(tuning))  // Uses tuning values
    .execute(() -> operation());

// Can update tuning at runtime
tuning.setMaxAttempts(10);  // Next retry will use new value
```

## Pattern 4: Policy Composition (Returns RetryPolicy)

These methods create or compose `RetryPolicy` instances:

### Budget Policy
```java
RetryBudget budget = new TokenBucketRetryBudget(10.0, 50.0);
RetryPolicy<String> basePolicy = (result, error, attempt, max) -> error != null;

// Compose budget with existing policy
RetryPolicy<String> budgetPolicy = RetryControls.withBudget(basePolicy, budget);

Retry.Builder<String> builder = Retry.newBuilder<String>()
    .policy(budgetPolicy)  // Use composed policy
    .execute(() -> operation())
    .getResult();
```

## Combining Multiple Controls

You can combine multiple controls:

```java
// Setup controls
Bulkhead bulkhead = new Bulkhead(10);
CircuitBreaker breaker = new CircuitBreaker(policy, Duration.ofSeconds(30));
RateLimiter limiter = new TokenBucketRateLimiter(100, 10);
RetryBudget budget = new TokenBucketRetryBudget(10.0, 50.0);

// Build retry with multiple controls
String result = Retry.newBuilder<String>()
    .name("protectedCall")
    .maxAttempts(5)
    .policy(RetryControls.withBudget(
        (r, e, a, m) -> e != null,
        budget
    ))
    .execute(
        RetryControls.circuitBreak(
            breaker,
            RetryControls.rateLimited(
                RetryControls.bulkhead(
                    bulkhead,
                    () -> apiCall()
                ),
                limiter
            )
        )
    )
    .getResult();
```

## Key Integration Points

1. **Builder methods** (`budget()`, `cache()`, `applyDynamicTuning()`) - Modify the builder directly
2. **Task wrappers** (`bulkhead()`, `rateLimited()`, `circuitBreak()`, etc.) - Wrap your task, use with `.execute()`
3. **Strategy creators** (`switchStop()`, `tunedStop()`) - Create strategies, use with `.stop()`
4. **Policy composers** (`withBudget()`) - Create policies, use with `.policy()`

All methods are designed to be composable and work together seamlessly.

