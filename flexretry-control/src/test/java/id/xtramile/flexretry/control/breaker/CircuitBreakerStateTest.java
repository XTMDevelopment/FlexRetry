package id.xtramile.flexretry.control.breaker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerStateTest {

    @Test
    void testEnumValues() {
        assertNotNull(CircuitBreakerState.CLOSED);
        assertNotNull(CircuitBreakerState.OPEN);
        assertNotNull(CircuitBreakerState.HALF_OPEN);

        assertEquals(3, CircuitBreakerState.values().length);
    }
}

