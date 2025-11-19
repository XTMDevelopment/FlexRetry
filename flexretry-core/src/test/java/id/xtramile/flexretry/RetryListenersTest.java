package id.xtramile.flexretry;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryListenersTest {

    @Test
    void testConstructor() {
        RetryListeners<String> listeners = new RetryListeners<>();
        assertNotNull(listeners);
    }

    @Test
    void testOnAttempt() {
        RetryListeners<String> listeners = new RetryListeners<>();
        AtomicInteger count = new AtomicInteger(0);
        
        listeners.onAttempt(ctx -> count.incrementAndGet());
        
        RetryContext<String> ctx = new RetryContext<>(
                "id", "name", 1, 3, null, null, Duration.ZERO, Map.of()
        );
        
        listeners.onAttempt.accept(ctx);
        assertEquals(1, count.get());
    }

    @Test
    void testOnSuccess() {
        RetryListeners<String> listeners = new RetryListeners<>();
        AtomicInteger count = new AtomicInteger(0);
        
        listeners.onSuccess((res, ctx) -> count.incrementAndGet());
        
        RetryContext<String> ctx = new RetryContext<>(
                "id", "name", 1, 3, "result", null, Duration.ZERO, Map.of()
        );
        
        listeners.onSuccess.accept("result", ctx);
        assertEquals(1, count.get());
    }

    @Test
    void testOnFailure() {
        RetryListeners<String> listeners = new RetryListeners<>();
        AtomicInteger count = new AtomicInteger(0);
        
        listeners.onFailure((error, ctx) -> count.incrementAndGet());
        
        RetryContext<String> ctx = new RetryContext<>(
                "id", "name", 1, 3, null, new RuntimeException("error"), Duration.ZERO, Map.of()
        );
        
        listeners.onFailure.accept(new RuntimeException("error"), ctx);
        assertEquals(1, count.get());
    }

    @Test
    void testOnFinally() {
        RetryListeners<String> listeners = new RetryListeners<>();
        AtomicInteger count = new AtomicInteger(0);
        
        listeners.onFinally(ctx -> count.incrementAndGet());
        
        RetryContext<String> ctx = new RetryContext<>(
                "id", "name", 1, 3, null, null, Duration.ZERO, Map.of()
        );
        
        listeners.onFinally.accept(ctx);
        assertEquals(1, count.get());
    }

    @Test
    void testBeforeSleep() {
        RetryListeners<String> listeners = new RetryListeners<>();
        AtomicInteger count = new AtomicInteger(0);
        
        listeners.beforeSleep = (duration, ctx) -> {
            count.incrementAndGet();
            return duration;
        };
        
        RetryContext<String> ctx = new RetryContext<>(
                "id", "name", 1, 3, null, null, Duration.ofMillis(100), Map.of()
        );
        
        Duration result = listeners.beforeSleep.apply(Duration.ofMillis(100), ctx);
        assertEquals(1, count.get());
        assertEquals(Duration.ofMillis(100), result);
    }

    @Test
    void testOnAttemptMethod() {
        RetryListeners<String> listeners = new RetryListeners<>();
        AtomicInteger count = new AtomicInteger(0);
        
        RetryListeners<String> result = listeners.onAttempt(ctx -> count.incrementAndGet());
        assertSame(listeners, result);
        
        RetryContext<String> ctx = new RetryContext<>(
                "id", "name", 1, 3, null, null, Duration.ZERO, Map.of()
        );
        
        listeners.onAttempt.accept(ctx);
        assertEquals(1, count.get());
    }

    @Test
    void testOnSuccessMethod() {
        RetryListeners<String> listeners = new RetryListeners<>();
        AtomicInteger count = new AtomicInteger(0);
        
        RetryListeners<String> result = listeners.onSuccess((res, ctx) -> count.incrementAndGet());
        assertSame(listeners, result);
        
        RetryContext<String> ctx = new RetryContext<>(
                "id", "name", 1, 3, "result", null, Duration.ZERO, Map.of()
        );
        
        listeners.onSuccess.accept("result", ctx);
        assertEquals(1, count.get());
    }

    @Test
    void testOnFailureMethod() {
        RetryListeners<String> listeners = new RetryListeners<>();
        AtomicInteger count = new AtomicInteger(0);
        
        RetryListeners<String> result = listeners.onFailure((error, ctx) -> count.incrementAndGet());
        assertSame(listeners, result);
        
        RetryContext<String> ctx = new RetryContext<>(
                "id", "name", 1, 3, null, new RuntimeException("error"), Duration.ZERO, Map.of()
        );
        
        listeners.onFailure.accept(new RuntimeException("error"), ctx);
        assertEquals(1, count.get());
    }

    @Test
    void testOnFinallyMethod() {
        RetryListeners<String> listeners = new RetryListeners<>();
        AtomicInteger count = new AtomicInteger(0);
        
        RetryListeners<String> result = listeners.onFinally(ctx -> count.incrementAndGet());
        assertSame(listeners, result);
        
        RetryContext<String> ctx = new RetryContext<>(
                "id", "name", 1, 3, null, null, Duration.ZERO, Map.of()
        );
        
        listeners.onFinally.accept(ctx);
        assertEquals(1, count.get());
    }
}

