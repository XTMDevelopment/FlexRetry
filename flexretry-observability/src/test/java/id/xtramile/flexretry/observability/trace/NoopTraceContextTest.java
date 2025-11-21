package id.xtramile.flexretry.observability.trace;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class NoopTraceContextTest {

    @Test
    void testInstance_IsSingleton() {
        NoopTraceContext instance1 = NoopTraceContext.INSTANCE;
        NoopTraceContext instance2 = NoopTraceContext.INSTANCE;

        assertSame(instance1, instance2);
    }

    @Test
    void testWithSpan_ReturnsNoopTraceScope() {
        NoopTraceContext context = NoopTraceContext.INSTANCE;

        TraceScope scope1 = context.withSpan("test.operation", Map.of("key", "value"));
        TraceScope scope2 = context.withSpan("another.operation", Collections.emptyMap());

        assertNotNull(scope1);
        assertNotNull(scope2);
        assertSame(NoopTraceScope.INSTANCE, scope1);
        assertSame(NoopTraceScope.INSTANCE, scope2);
    }

    @Test
    void testWithSpan_WithNullAttributes() {
        NoopTraceContext context = NoopTraceContext.INSTANCE;

        TraceScope scope = context.withSpan("test.operation", null);

        assertNotNull(scope);
        assertSame(NoopTraceScope.INSTANCE, scope);
    }

    @Test
    void testWithSpan_WithEmptyAttributes() {
        NoopTraceContext context = NoopTraceContext.INSTANCE;

        TraceScope scope = context.withSpan("test.operation", Collections.emptyMap());

        assertNotNull(scope);
        assertSame(NoopTraceScope.INSTANCE, scope);
    }

    @Test
    void testWithSpan_WithDifferentOperationNames() {
        NoopTraceContext context = NoopTraceContext.INSTANCE;

        TraceScope scope1 = context.withSpan("operation1", Map.of());
        TraceScope scope2 = context.withSpan("operation2", Map.of());

        assertSame(NoopTraceScope.INSTANCE, scope1);
        assertSame(NoopTraceScope.INSTANCE, scope2);
    }
}

