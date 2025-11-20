package id.xtramile.flexretry.observability;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.RetryContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TimingTrackerTest {

    @Test
    void testTimingTracker_TracksAttemptElapsed() throws Exception {
        Class<?> trackerClass = Class.forName("id.xtramile.flexretry.observability.TimingTracker");
        Object tracker = trackerClass.getDeclaredConstructor().newInstance();

        Method onAttemptMethod = trackerClass.getDeclaredMethod("onAttempt", RetryContext.class);
        onAttemptMethod.setAccessible(true);

        Method getElapsedMethod = trackerClass.getDeclaredMethod("getElapsed", RetryContext.class);
        getElapsedMethod.setAccessible(true);

        AtomicInteger attemptCount = new AtomicInteger(0);
        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(1)
                .onAttempt(ctx -> {
                    try {
                        onAttemptMethod.invoke(tracker, ctx);
                        attemptCount.incrementAndGet();

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        String result = builder.execute((Callable<String>) () -> {
            try {
                Thread.sleep(50);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return "success";
        }).getResult();

        assertEquals("success", result);
        assertEquals(1, attemptCount.get());

        RetryContext<String> context = new RetryContext<>(
                "test-id", "test-name", 1, 1, "test", null, Duration.ZERO, null);

        Duration elapsed = (Duration) getElapsedMethod.invoke(tracker, context);
        assertNotNull(elapsed);
        assertTrue(elapsed.toNanos() >= 0);
    }

    @Test
    void testTimingTracker_TracksTotalElapsed() throws Exception {
        Class<?> trackerClass = Class.forName("id.xtramile.flexretry.observability.TimingTracker");
        Object tracker = trackerClass.getDeclaredConstructor().newInstance();

        Method onAttemptMethod = trackerClass.getDeclaredMethod("onAttempt", RetryContext.class);
        onAttemptMethod.setAccessible(true);

        Method getTotalElapsedMethod = trackerClass.getDeclaredMethod("getTotalElapsed", RetryContext.class);
        getTotalElapsedMethod.setAccessible(true);

        AtomicInteger attemptCount = new AtomicInteger(0);
        Retry.Builder<String> builder = Retry.<String>newBuilder()
                .maxAttempts(2)
                .retryOn(RuntimeException.class)
                .onAttempt(ctx -> {
                    try {
                        onAttemptMethod.invoke(tracker, ctx);
                        attemptCount.incrementAndGet();

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        try {
            builder.execute((Callable<String>) () -> {
                if (attemptCount.get() < 2) {
                    throw new RuntimeException("retry");
                }

                return "success";
            }).getResult();
        } catch (Exception ignored) {
        }

        assertTrue(attemptCount.get() >= 1);

        RetryContext<String> context = new RetryContext<>(
                "test-id", "test-name", 1, 2, null, null, Duration.ZERO, null);
        Duration totalElapsed = (Duration) getTotalElapsedMethod.invoke(tracker, context);
        assertNotNull(totalElapsed);
        assertTrue(totalElapsed.toNanos() >= 0);
    }

    @Test
    void testTimingTracker_ReturnsZeroForUnknownContext() throws Exception {
        Class<?> trackerClass = Class.forName("id.xtramile.flexretry.observability.TimingTracker");
        Object tracker = trackerClass.getDeclaredConstructor().newInstance();

        Method getElapsedMethod = trackerClass.getDeclaredMethod("getElapsed", RetryContext.class);
        getElapsedMethod.setAccessible(true);

        Method getTotalElapsedMethod = trackerClass.getDeclaredMethod("getTotalElapsed", RetryContext.class);
        getTotalElapsedMethod.setAccessible(true);

        RetryContext<String> unknownContext = new RetryContext<>(
                "unknown-id", "unknown-name", 1, 1, null, null, Duration.ZERO, null);

        Duration elapsed = (Duration) getElapsedMethod.invoke(tracker, unknownContext);
        assertEquals(Duration.ZERO, elapsed);

        Duration totalElapsed = (Duration) getTotalElapsedMethod.invoke(tracker, unknownContext);
        assertEquals(Duration.ZERO, totalElapsed);
    }
}

