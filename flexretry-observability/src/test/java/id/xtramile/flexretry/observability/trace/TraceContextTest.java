package id.xtramile.flexretry.observability.trace;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TraceContextTest {

    @Test
    void testNoop_ReturnsNoopInstance() {
        TraceContext noop1 = TraceContext.noop();
        TraceContext noop2 = TraceContext.noop();

        assertNotNull(noop1);
        assertNotNull(noop2);
        assertSame(noop1.getClass(), noop2.getClass());
    }

    @Test
    void testDefaultWithSpan_WithOperationNameOnly() {
        TraceContext context = (operationName, attributes) -> SimpleTraceScope.create(() -> {
        });

        TraceScope scope = context.withSpan("test.operation");

        assertNotNull(scope);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testDefaultWithSpan_UsesEmptyMap() {
        final boolean[] called = new boolean[1];
        final Map<String, String>[] capturedAttributes = new Map[1];

        TraceContext context = (operationName, attributes) -> {
            called[0] = true;
            capturedAttributes[0] = attributes;
            return SimpleTraceScope.create(() -> {
            });
        };

        context.withSpan("test.operation");

        assertTrue(called[0]);
        assertEquals(Collections.emptyMap(), capturedAttributes[0]);
    }

    @Test
    void testNoop_WithSpan_ReturnsNoopScope() {
        TraceContext noop = TraceContext.noop();

        TraceScope scope1 = noop.withSpan("test.operation");
        TraceScope scope2 = noop.withSpan("test.operation", Map.of("key", "value"));

        assertNotNull(scope1);
        assertNotNull(scope2);
        assertSame(NoopTraceScope.INSTANCE, scope1);
        assertSame(NoopTraceScope.INSTANCE, scope2);
    }
}

