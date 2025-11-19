package id.xtramile.flexretry.control.concurrency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyLimitedExceptionTest {

    @Test
    void testConstructor() {
        ConcurrencyLimitedException exception = new ConcurrencyLimitedException("concurrency limited");
        
        assertNotNull(exception);
        assertEquals("concurrency limited", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testConstructor_WithMessage() {
        String message = "Concurrency limit exceeded";
        ConcurrencyLimitedException exception = new ConcurrencyLimitedException(message);
        
        assertEquals(message, exception.getMessage());
    }

    @Test
    void testIsRuntimeException() {
        ConcurrencyLimitedException exception = new ConcurrencyLimitedException("test");

        assertInstanceOf(RuntimeException.class, exception);
    }
}

