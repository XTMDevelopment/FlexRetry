package id.xtramile.flexretry.control.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitExceededExceptionTest {

    @Test
    void testConstructor() {
        RateLimitExceededException exception = new RateLimitExceededException("rate limit exceeded");
        
        assertNotNull(exception);
        assertEquals("rate limit exceeded", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testConstructor_WithMessage() {
        String message = "Rate limit exceeded";
        RateLimitExceededException exception = new RateLimitExceededException(message);
        
        assertEquals(message, exception.getMessage());
    }

    @Test
    void testIsRuntimeException() {
        RateLimitExceededException exception = new RateLimitExceededException("test");

        assertInstanceOf(RuntimeException.class, exception);
    }
}

