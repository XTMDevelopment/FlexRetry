package id.xtramile.flexretry.observability.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetryEventBusTest {

    @Test
    void testNoop_ReturnsNoopInstance() {
        RetryEventBus<String> noop1 = RetryEventBus.noop();
        RetryEventBus<String> noop2 = RetryEventBus.noop();

        assertNotNull(noop1);
        assertNotNull(noop2);
        assertSame(noop1.getClass(), noop2.getClass());
    }

    @Test
    void testNoop_WithDifferentTypes_ReturnsNoopInstance() {
        RetryEventBus<String> noopString = RetryEventBus.noop();
        RetryEventBus<Integer> noopInt = RetryEventBus.noop();
        RetryEventBus<Object> noopObject = RetryEventBus.noop();

        assertNotNull(noopString);
        assertNotNull(noopInt);
        assertNotNull(noopObject);
    }

    @Test
    void testNoop_Publish_DoesNothing() {
        RetryEventBus<String> bus = RetryEventBus.noop();
        RetryEvent<String> event = RetryEvent.<String>builder(RetryEventType.RETRY_ATTEMPT)
                .attempt(1)
                .build();

        assertDoesNotThrow(() -> bus.publish(event));
        assertDoesNotThrow(() -> bus.publish(null));
    }

    @Test
    void testNoop_Register_DoesNothing() {
        RetryEventBus<String> bus = RetryEventBus.noop();
        RetryEventListener<String> listener = event -> fail("Should not be called");

        assertDoesNotThrow(() -> bus.register(listener));
        assertDoesNotThrow(() -> bus.register(null));
    }

    @Test
    void testNoop_Unregister_DoesNothing() {
        RetryEventBus<String> bus = RetryEventBus.noop();
        RetryEventListener<String> listener = event -> {
        };

        assertDoesNotThrow(() -> bus.unregister(listener));
        assertDoesNotThrow(() -> bus.unregister(null));
    }
}

