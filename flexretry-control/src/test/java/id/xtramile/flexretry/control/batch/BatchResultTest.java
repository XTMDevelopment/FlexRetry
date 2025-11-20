package id.xtramile.flexretry.control.batch;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BatchResultTest {

    @Test
    void testConstructor() {
        List<String> successes = Arrays.asList("success1", "success2");
        List<String> failures = List.of("failure1");
        
        BatchResult<String> result = new BatchResult<>(successes, failures);
        
        assertEquals(successes, result.successes);
        assertEquals(failures, result.failures);
    }

    @Test
    void testIsComplete_NoFailures() {
        List<String> successes = Arrays.asList("success1", "success2");
        
        BatchResult<String> result = new BatchResult<>(successes, null);
        
        assertTrue(result.isComplete());
    }

    @Test
    void testIsComplete_EmptyFailures() {
        List<String> successes = Arrays.asList("success1", "success2");
        List<String> failures = Collections.emptyList();
        
        BatchResult<String> result = new BatchResult<>(successes, failures);
        
        assertTrue(result.isComplete());
    }

    @Test
    void testIsComplete_WithFailures() {
        List<String> successes = List.of("success1");
        List<String> failures = List.of("failure1");
        
        BatchResult<String> result = new BatchResult<>(successes, failures);
        
        assertFalse(result.isComplete());
    }

    @Test
    void testIsComplete_EmptySuccesses() {
        List<String> successes = Collections.emptyList();
        List<String> failures = Collections.emptyList();
        
        BatchResult<String> result = new BatchResult<>(successes, failures);
        
        assertTrue(result.isComplete());
    }
}

