package id.xtramile.flexretry.observability.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NoopRetryEventBusTest {

    @Test
    void testInstance_IsSingleton() {
        NoopRetryEventBus instance1 = NoopRetryEventBus.INSTANCE;
        NoopRetryEventBus instance2 = NoopRetryEventBus.INSTANCE;

        assertSame(instance1, instance2);
    }

    @Test
    void testPublish_DoesNothing() {
        NoopRetryEventBus bus = NoopRetryEventBus.INSTANCE;
        RetryEvent<Object> event = RetryEvent.builder(RetryEventType.RETRY_ATTEMPT)
                .attempt(1)
                .build();

        assertDoesNotThrow(() -> bus.publish(event));
        assertDoesNotThrow(() -> bus.publish(null));
    }

    @Test
    void testRegister_DoesNothing() {
        NoopRetryEventBus bus = NoopRetryEventBus.INSTANCE;
        RetryEventListener<Object> listener = event -> fail("Should not be called");

        assertDoesNotThrow(() -> bus.register(listener));
        assertDoesNotThrow(() -> bus.register(null));
    }

    @Test
    void testUnregister_DoesNothing() {
        NoopRetryEventBus bus = NoopRetryEventBus.INSTANCE;
        RetryEventListener<Object> listener = event -> {
        };

        assertDoesNotThrow(() -> bus.unregister(listener));
        assertDoesNotThrow(() -> bus.unregister(null));
    }

    @Test
    void testPublish_AfterRegister_DoesNotCallListener() {
        NoopRetryEventBus bus = NoopRetryEventBus.INSTANCE;
        RetryEventListener<Object> listener = event -> fail("Should not be called");

        bus.register(listener);

        RetryEvent<Object> event = RetryEvent.builder(RetryEventType.RETRY_ATTEMPT)
                .attempt(1)
                .build();

        assertDoesNotThrow(() -> bus.publish(event));
    }
}

