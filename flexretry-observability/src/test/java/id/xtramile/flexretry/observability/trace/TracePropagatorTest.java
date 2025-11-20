package id.xtramile.flexretry.observability.trace;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TracePropagatorTest {

    @Test
    void testInject_WithMapCarrier() {
        Map<String, String> carrier = new HashMap<>();
        TracePropagator<Map<String, String>> propagator = new TracePropagator<>() {
            @Override
            public void inject(Map<String, String> traceContext, Map<String, String> carrier) {
                carrier.putAll(traceContext);
            }

            @Override
            public Map<String, String> extract(Map<String, String> carrier) {
                return new HashMap<>(carrier);
            }
        };

        Map<String, String> traceContext = Map.of(
                "trace-id", "12345",
                "span-id", "67890"
        );

        propagator.inject(traceContext, carrier);

        assertEquals("12345", carrier.get("trace-id"));
        assertEquals("67890", carrier.get("span-id"));
    }

    @Test
    void testExtract_WithMapCarrier() {
        Map<String, String> carrier = new HashMap<>();
        carrier.put("trace-id", "12345");
        carrier.put("span-id", "67890");

        TracePropagator<Map<String, String>> propagator = new TracePropagator<>() {
            @Override
            public void inject(Map<String, String> traceContext, Map<String, String> carrier) {
                carrier.putAll(traceContext);
            }

            @Override
            public Map<String, String> extract(Map<String, String> carrier) {
                return new HashMap<>(carrier);
            }
        };

        Map<String, String> extracted = propagator.extract(carrier);

        assertEquals("12345", extracted.get("trace-id"));
        assertEquals("67890", extracted.get("span-id"));
    }

    @Test
    void testInject_WithEmptyContext() {
        Map<String, String> carrier = new HashMap<>();
        TracePropagator<Map<String, String>> propagator = new TracePropagator<>() {
            @Override
            public void inject(Map<String, String> traceContext, Map<String, String> carrier) {
                carrier.putAll(traceContext);
            }

            @Override
            public Map<String, String> extract(Map<String, String> carrier) {
                return new HashMap<>(carrier);
            }
        };

        propagator.inject(Map.of(), carrier);

        assertTrue(carrier.isEmpty());
    }

    @Test
    void testExtract_WithEmptyCarrier() {
        Map<String, String> carrier = new HashMap<>();
        TracePropagator<Map<String, String>> propagator = new TracePropagator<>() {
            @Override
            public void inject(Map<String, String> traceContext, Map<String, String> carrier) {
                carrier.putAll(traceContext);
            }

            @Override
            public Map<String, String> extract(Map<String, String> carrier) {
                return new HashMap<>(carrier);
            }
        };

        Map<String, String> extracted = propagator.extract(carrier);

        assertTrue(extracted.isEmpty());
    }

    @Test
    void testInject_OverwritesExistingValues() {
        Map<String, String> carrier = new HashMap<>();
        carrier.put("trace-id", "old-value");

        TracePropagator<Map<String, String>> propagator = new TracePropagator<>() {
            @Override
            public void inject(Map<String, String> traceContext, Map<String, String> carrier) {
                carrier.putAll(traceContext);
            }

            @Override
            public Map<String, String> extract(Map<String, String> carrier) {
                return new HashMap<>(carrier);
            }
        };

        Map<String, String> traceContext = Map.of("trace-id", "new-value");
        propagator.inject(traceContext, carrier);

        assertEquals("new-value", carrier.get("trace-id"));
    }
}

