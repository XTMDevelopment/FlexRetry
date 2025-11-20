package id.xtramile.flexretry.control.bulkhead;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BulkheadFullExceptionTest {

    @Test
    void testConstructor() {
        BulkheadFullException exception = new BulkheadFullException("bulkhead full");
        
        assertNotNull(exception);
        assertEquals("bulkhead full", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testConstructor_WithMessage() {
        String message = "Bulkhead is full";
        BulkheadFullException exception = new BulkheadFullException(message);
        
        assertEquals(message, exception.getMessage());
    }

    @Test
    void testIsRuntimeException() {
        BulkheadFullException exception = new BulkheadFullException("test");

        assertInstanceOf(RuntimeException.class, exception);
    }
}

