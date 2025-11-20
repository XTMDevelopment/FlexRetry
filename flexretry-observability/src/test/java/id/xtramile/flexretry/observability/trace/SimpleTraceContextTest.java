package id.xtramile.flexretry.observability.trace;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class SimpleTraceContextTest {

    @Test
    void testBuilder() {
        SimpleTraceContext.Builder builder = SimpleTraceContext.builder();

        assertNotNull(builder);
    }

    @Test
    void testCreate_WithSpanFactory() {
        AtomicInteger callCount = new AtomicInteger(0);
        BiFunction<String, Map<String, String>, TraceScope> spanFactory = (name, attributes) -> {
            callCount.incrementAndGet();
            return SimpleTraceScope.create(() -> {
            });
        };

        SimpleTraceContext context = SimpleTraceContext.create(spanFactory);
        assertNotNull(context);

        TraceScope scope = context.withSpan("test.operation", Map.of("key", "value"));
        assertNotNull(scope);
        assertEquals(1, callCount.get());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testWithSpan_CallsSpanFactory() {
        AtomicInteger callCount = new AtomicInteger(0);
        String[] capturedName = new String[1];
        Map<String, String>[] capturedAttributes = new Map[1];

        BiFunction<String, Map<String, String>, TraceScope> spanFactory = (name, attributes) -> {
            callCount.incrementAndGet();
            capturedName[0] = name;
            capturedAttributes[0] = attributes;

            return SimpleTraceScope.create(() -> {
            });
        };

        SimpleTraceContext context = SimpleTraceContext.builder()
                .spanFactory(spanFactory)
                .build();

        Map<String, String> attributes = Map.of("key1", "value1", "key2", "value2");
        TraceScope scope = context.withSpan("test.operation", attributes);

        assertNotNull(scope);
        assertEquals(1, callCount.get());
        assertEquals("test.operation", capturedName[0]);
        assertEquals(attributes, capturedAttributes[0]);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testWithSpan_WithNullAttributes_UsesEmptyMap() {
        AtomicInteger callCount = new AtomicInteger(0);
        Map<String, String>[] capturedAttributes = new Map[1];

        BiFunction<String, Map<String, String>, TraceScope> spanFactory = (name, attributes) -> {
            callCount.incrementAndGet();
            capturedAttributes[0] = attributes;

            return SimpleTraceScope.create(() -> {
            });
        };

        SimpleTraceContext context = SimpleTraceContext.builder()
                .spanFactory(spanFactory)
                .build();

        TraceScope scope = context.withSpan("test.operation", null);

        assertNotNull(scope);
        assertEquals(1, callCount.get());
        assertEquals(Collections.emptyMap(), capturedAttributes[0]);
    }

    @SuppressWarnings("WriteOnlyObject")
    @Test
    void testBuilder_SpanFactory_WithNull_ThrowsException() {
        SimpleTraceContext.Builder builder = SimpleTraceContext.builder();

        assertThrows(NullPointerException.class, () -> builder.spanFactory(null));
    }

    @Test
    void testBuild_WithNullSpanFactory_ThrowsException() {
        SimpleTraceContext.Builder builder = SimpleTraceContext.builder();

        assertThrows(NullPointerException.class, builder::build);
    }

    @Test
    void testBuild_WithValidSpanFactory() {
        BiFunction<String, Map<String, String>, TraceScope> spanFactory = (name, attributes) ->
                SimpleTraceScope.create(() -> {
                });

        SimpleTraceContext context = SimpleTraceContext.builder()
                .spanFactory(spanFactory)
                .build();

        assertNotNull(context);
    }

    @Test
    void testWithSpan_ReturnsTraceScope() {
        TraceScope mockScope = SimpleTraceScope.create(() -> {
        });

        BiFunction<String, Map<String, String>, TraceScope> spanFactory = (name, attributes) -> mockScope;

        SimpleTraceContext context = SimpleTraceContext.builder()
                .spanFactory(spanFactory)
                .build();

        TraceScope scope = context.withSpan("test.operation", Map.of());
        assertSame(mockScope, scope);
    }
}

