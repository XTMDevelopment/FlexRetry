package id.xtramile.flexretry.observability.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetryEventTypeTest {

    @Test
    void testAllEventTypesExist() {
        RetryEventType[] types = RetryEventType.values();

        assertEquals(5, types.length);
    }

    @Test
    void testValueOf() {
        assertEquals(RetryEventType.RETRY_SCHEDULED, RetryEventType.valueOf("RETRY_SCHEDULED"));
        assertEquals(RetryEventType.RETRY_ATTEMPT, RetryEventType.valueOf("RETRY_ATTEMPT"));
        assertEquals(RetryEventType.RETRY_SUCCESS, RetryEventType.valueOf("RETRY_SUCCESS"));
        assertEquals(RetryEventType.RETRY_FAILURE, RetryEventType.valueOf("RETRY_FAILURE"));
        assertEquals(RetryEventType.RETRY_GIVE_UP, RetryEventType.valueOf("RETRY_GIVE_UP"));
    }

    @Test
    void testValues() {
        RetryEventType[] types = RetryEventType.values();

        assertTrue(contains(types, RetryEventType.RETRY_SCHEDULED));
        assertTrue(contains(types, RetryEventType.RETRY_ATTEMPT));
        assertTrue(contains(types, RetryEventType.RETRY_SUCCESS));
        assertTrue(contains(types, RetryEventType.RETRY_FAILURE));
        assertTrue(contains(types, RetryEventType.RETRY_GIVE_UP));
    }

    @Test
    void testValueOf_InvalidName_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> RetryEventType.valueOf("INVALID_TYPE"));
    }

    private boolean contains(RetryEventType[] types, RetryEventType type) {
        for (RetryEventType t : types) {
            if (t == type) {
                return true;
            }
        }

        return false;
    }
}

