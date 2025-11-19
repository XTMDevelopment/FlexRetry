package id.xtramile.flexretry.control.batch;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BatchRetryStrategyTest {

    @Test
    void testOf() {
        BatchRetryStrategy<String> strategy = BatchRetryStrategy.of(
            list -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(list.get(i));
                }
                return sb.toString();
            },
            s -> s
        );
        
        assertNotNull(strategy);
    }

    @Test
    void testReduce() {
        BatchRetryStrategy<String> strategy = BatchRetryStrategy.of(
            list -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(list.get(i));
                }
                return sb.toString();
            },
            s -> s
        );
        
        String original = "original";
        List<String> failures = Arrays.asList("failure1", "failure2", "failure3");
        
        String result = strategy.reduce(original, failures);
        
        assertEquals("failure1,failure2,failure3", result);
    }

    @Test
    void testReduce_EmptyFailures() {
        BatchRetryStrategy<String> strategy = BatchRetryStrategy.of(
            list -> {
                if (list.isEmpty()) {
                    return "empty";
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(list.get(i));
                }
                return sb.toString();
            },
            s -> s
        );
        
        String original = "original";
        List<String> failures = List.of();
        
        String result = strategy.reduce(original, failures);
        
        assertEquals("empty", result);
    }

    @Test
    void testReduce_IntegerStrategy() {
        BatchRetryStrategy<Integer> strategy = BatchRetryStrategy.of(
            list -> {
                int sum = 0;
                for (Object o : list) {
                    sum += (Integer) o;
                }
                return sum;
            },
            i -> i
        );
        
        Integer original = 100;
        List<Integer> failures = Arrays.asList(1, 2, 3, 4, 5);
        
        Integer result = strategy.reduce(original, failures);
        
        assertEquals(15, result);
    }
}

