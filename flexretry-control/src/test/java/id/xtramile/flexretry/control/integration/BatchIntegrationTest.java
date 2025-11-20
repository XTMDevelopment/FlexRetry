package id.xtramile.flexretry.control.integration;

import id.xtramile.flexretry.control.batch.BatchRetryStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for BatchRetryStrategy.
 * Demonstrates custom BatchRetryStrategy implementations.
 */
class BatchIntegrationTest {

    /**
     * Custom BatchRetryStrategy that retries only failed items
     */
    static class RetryFailedOnlyStrategy<I> implements BatchRetryStrategy<I> {
        private final Function<List<?>, I> reducer;

        RetryFailedOnlyStrategy(Function<List<?>, I> reducer) {
            this.reducer = reducer;
        }

        @Override
        public I reduce(I originalInput, List<?> failures) {
            if (failures.isEmpty()) {
                return originalInput;
            }
            return reducer.apply(failures);
        }
    }

    /**
     * Custom BatchRetryStrategy that combines original and failures
     */
    static class CombineStrategy<I> implements BatchRetryStrategy<I> {
        private final Function<I, List<?>> extractor;
        private final Function<List<?>, I> combiner;

        CombineStrategy(Function<I, List<?>> extractor, Function<List<?>, I> combiner) {
            this.extractor = extractor;
            this.combiner = combiner;
        }

        @Override
        public I reduce(I originalInput, List<?> failures) {
            if (failures.isEmpty()) {
                return originalInput;
            }
            List<?> original = extractor.apply(originalInput);
            List<Object> combined = new ArrayList<>(original);
            combined.addAll(failures);
            return combiner.apply(combined);
        }
    }

    /**
     * Custom BatchRetryStrategy with limit on retry size
     */
    static class LimitedRetryStrategy<I> implements BatchRetryStrategy<I> {
        private final int maxRetrySize;
        private final Function<List<?>, I> reducer;

        LimitedRetryStrategy(int maxRetrySize, Function<List<?>, I> reducer) {
            this.maxRetrySize = maxRetrySize;
            this.reducer = reducer;
        }

        @Override
        public I reduce(I originalInput, List<?> failures) {
            if (failures.isEmpty()) {
                return originalInput;
            }

            List<?> limited = failures.size() > maxRetrySize
                ? failures.subList(0, maxRetrySize)
                : failures;

            return reducer.apply(limited);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void testBatchRetryStrategy_Of() {
        BatchRetryStrategy<List<String>> strategy = BatchRetryStrategy.of(
            failures -> (List<String>) failures,
            list -> list
        );

        List<String> original = List.of("item1", "item2", "item3");
        List<String> failures = List.of("item2", "item3");

        List<String> result = strategy.reduce(original, failures);
        assertEquals(failures, result);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testBatchRetryStrategy_Of_EmptyFailures() {
        BatchRetryStrategy<List<String>> strategy = BatchRetryStrategy.of(
            failures -> (List<String>) failures,
            list -> list
        );

        List<String> original = List.of("item1", "item2");
        List<String> failures = List.of();

        List<String> result = strategy.reduce(original, failures);
        assertEquals(original, result);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testCustomRetryFailedOnlyStrategy() {
        RetryFailedOnlyStrategy<List<String>> strategy = new RetryFailedOnlyStrategy<>(
            failures -> (List<String>) failures
        );

        List<String> original = List.of("item1", "item2", "item3", "item4");
        List<String> failures = List.of("item2", "item4");

        List<String> result = strategy.reduce(original, failures);
        assertEquals(failures, result);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testCustomRetryFailedOnlyStrategy_EmptyFailures() {
        RetryFailedOnlyStrategy<List<String>> strategy = new RetryFailedOnlyStrategy<>(
            failures -> (List<String>) failures
        );

        List<String> original = List.of("item1", "item2");
        List<String> failures = List.of();

        List<String> result = strategy.reduce(original, failures);
        assertEquals(original, result);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testCustomCombineStrategy() {
        CombineStrategy<List<String>> strategy = new CombineStrategy<>(
            list -> list,
            list -> (List<String>) list
        );

        List<String> original = List.of("item1", "item2");
        List<String> failures = List.of("item3", "item4");

        List<String> result = strategy.reduce(original, failures);
        assertEquals(4, result.size());
        assertTrue(result.contains("item1"));
        assertTrue(result.contains("item2"));
        assertTrue(result.contains("item3"));
        assertTrue(result.contains("item4"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testCustomLimitedRetryStrategy() {
        LimitedRetryStrategy<List<String>> strategy = new LimitedRetryStrategy<>(
            2,
            failures -> (List<String>) failures
        );

        List<String> original = List.of("item1", "item2", "item3", "item4", "item5");
        List<String> failures = List.of("item2", "item3", "item4", "item5");

        List<String> result = strategy.reduce(original, failures);
        assertEquals(2, result.size());
        assertEquals("item2", result.get(0));
        assertEquals("item3", result.get(1));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testCustomLimitedRetryStrategy_WithinLimit() {
        LimitedRetryStrategy<List<String>> strategy = new LimitedRetryStrategy<>(
            5,
            failures -> (List<String>) failures
        );

        List<String> original = List.of("item1", "item2", "item3");
        List<String> failures = List.of("item2", "item3");

        List<String> result = strategy.reduce(original, failures);
        assertEquals(2, result.size());
        assertEquals(failures, result);
    }
}