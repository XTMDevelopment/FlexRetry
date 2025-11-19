package id.xtramile.flexretry.control.breaker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CircuitOpenExceptionTest {

    @Test
    void testConstructor() {
        CircuitOpenException exception = new CircuitOpenException("circuit open");
        
        assertNotNull(exception);
        assertEquals("circuit open", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testConstructor_WithMessage() {
        String message = "Circuit breaker is open";
        CircuitOpenException exception = new CircuitOpenException(message);
        
        assertEquals(message, exception.getMessage());
    }

    @Test
    void testIsRuntimeException() {
        CircuitOpenException exception = new CircuitOpenException("test");

        assertInstanceOf(RuntimeException.class, exception);
    }
}

