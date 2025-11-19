package id.xtramile.flexretry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetryOutcomeTest {

    @Test
    void testSuccessOutcome() {
        String result = "success";
        RetryOutcome<String> outcome = new RetryOutcome<>(true, result, null, 1);

        assertTrue(outcome.isSuccess());
        assertEquals(result, outcome.result());
        assertNull(outcome.error());
        assertEquals(1, outcome.attempts());
        assertNull(outcome.message());
    }

    @Test
    void testFailureOutcome() {
        Throwable error = new RuntimeException("error");
        RetryOutcome<String> outcome = new RetryOutcome<>(false, null, error, 3);

        assertFalse(outcome.isSuccess());
        assertNull(outcome.result());
        assertEquals(error, outcome.error());
        assertEquals(3, outcome.attempts());
        assertNull(outcome.message());
    }

    @Test
    void testOutcomeWithMessage() {
        String message = "Custom error message";
        RetryOutcome<String> outcome = new RetryOutcome<>(false, null, null, 2, message);

        assertFalse(outcome.isSuccess());
        assertEquals(message, outcome.message());
        assertEquals(2, outcome.attempts());
    }

    @Test
    void testOutcomeWithResultAndMessage() {
        String result = "result";
        String message = "Success message";
        RetryOutcome<String> outcome = new RetryOutcome<>(true, result, null, 1, message);

        assertTrue(outcome.isSuccess());
        assertEquals(result, outcome.result());
        assertEquals(message, outcome.message());
    }
}

