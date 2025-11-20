package id.xtramile.flexretry.observability.events;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SimpleRetryEventBusTest {

    @Test
    void testCreate() {
        SimpleRetryEventBus<String> bus = SimpleRetryEventBus.create();
        assertNotNull(bus);
    }

    @Test
    void testPublish_WithNoListeners() {
        SimpleRetryEventBus<String> bus = SimpleRetryEventBus.create();
        RetryEvent<String> event = RetryEvent.<String>builder(RetryEventType.RETRY_ATTEMPT)
                .attempt(1)
                .build();

        assertDoesNotThrow(() -> bus.publish(event));
    }

    @Test
    void testPublish_WithNullEvent() {
        SimpleRetryEventBus<String> bus = SimpleRetryEventBus.create();
        RetryEventListener<String> listener = event -> fail("Should not be called");

        bus.register(listener);

        assertDoesNotThrow(() -> bus.publish(null));
    }

    @Test
    void testPublish_WithSingleListener() {
        SimpleRetryEventBus<String> bus = SimpleRetryEventBus.create();
        List<RetryEvent<String>> receivedEvents = new ArrayList<>();

        RetryEventListener<String> listener = receivedEvents::add;
        bus.register(listener);

        RetryEvent<String> event = RetryEvent.<String>builder(RetryEventType.RETRY_ATTEMPT)
                .attempt(1)
                .build();

        bus.publish(event);

        assertEquals(1, receivedEvents.size());
        assertEquals(event, receivedEvents.get(0));
    }

    @Test
    void testPublish_WithMultipleListeners() {
        SimpleRetryEventBus<String> bus = SimpleRetryEventBus.create();
        List<RetryEvent<String>> listener1Events = new ArrayList<>();
        List<RetryEvent<String>> listener2Events = new ArrayList<>();

        RetryEventListener<String> listener1 = listener1Events::add;
        RetryEventListener<String> listener2 = listener2Events::add;

        bus.register(listener1);
        bus.register(listener2);

        RetryEvent<String> event = RetryEvent.<String>builder(RetryEventType.RETRY_SUCCESS)
                .attempt(1)
                .build();

        bus.publish(event);

        assertEquals(1, listener1Events.size());
        assertEquals(1, listener2Events.size());
        assertEquals(event, listener1Events.get(0));
        assertEquals(event, listener2Events.get(0));
    }

    @Test
    void testPublish_ListenerException_DoesNotStopOtherListeners() {
        SimpleRetryEventBus<String> bus = SimpleRetryEventBus.create();
        AtomicInteger successfulListenerCount = new AtomicInteger(0);

        RetryEventListener<String> failingListener = event -> {
            throw new RuntimeException("Listener error");
        };

        RetryEventListener<String> successfulListener = event -> successfulListenerCount.incrementAndGet();

        bus.register(failingListener);
        bus.register(successfulListener);

        RetryEvent<String> event = RetryEvent.<String>builder(RetryEventType.RETRY_ATTEMPT)
                .attempt(1)
                .build();

        assertDoesNotThrow(() -> bus.publish(event));
        assertEquals(1, successfulListenerCount.get());
    }

    @Test
    void testRegister_WithNullListener_ThrowsException() {
        SimpleRetryEventBus<String> bus = SimpleRetryEventBus.create();
        assertThrows(NullPointerException.class, () -> bus.register(null));
    }

    @Test
    void testRegister_WithValidListener() {
        SimpleRetryEventBus<String> bus = SimpleRetryEventBus.create();
        RetryEventListener<String> listener = event -> {
        };

        assertDoesNotThrow(() -> bus.register(listener));
    }

    @Test
    void testUnregister_WithNullListener() {
        SimpleRetryEventBus<String> bus = SimpleRetryEventBus.create();

        assertDoesNotThrow(() -> bus.unregister(null));
    }

    @Test
    void testUnregister_RemovesListener() {
        SimpleRetryEventBus<String> bus = SimpleRetryEventBus.create();
        AtomicInteger callCount = new AtomicInteger(0);

        RetryEventListener<String> listener = event -> callCount.incrementAndGet();
        bus.register(listener);

        RetryEvent<String> event = RetryEvent.<String>builder(RetryEventType.RETRY_ATTEMPT)
                .attempt(1)
                .build();

        bus.publish(event);
        assertEquals(1, callCount.get());

        bus.unregister(listener);
        bus.publish(event);
        assertEquals(1, callCount.get());
    }

    @Test
    void testConcurrentPublish() throws InterruptedException {
        SimpleRetryEventBus<String> bus = SimpleRetryEventBus.create();
        AtomicInteger eventCount = new AtomicInteger(0);

        RetryEventListener<String> listener = event -> eventCount.incrementAndGet();
        bus.register(listener);

        int threadCount = 10;
        int eventsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    for (int j = 0; j < eventsPerThread; j++) {
                        RetryEvent<String> event = RetryEvent.<String>builder(RetryEventType.RETRY_ATTEMPT)
                                .attempt(j)
                                .build();
                        bus.publish(event);
                    }

                } finally {
                    latch.countDown();
                }

            }).start();
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(threadCount * eventsPerThread, eventCount.get());
    }
}

