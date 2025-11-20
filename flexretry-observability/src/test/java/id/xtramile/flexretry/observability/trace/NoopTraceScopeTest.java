package id.xtramile.flexretry.observability.trace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NoopTraceScopeTest {

    @Test
    void testInstance_IsSingleton() {
        NoopTraceScope instance1 = NoopTraceScope.INSTANCE;
        NoopTraceScope instance2 = NoopTraceScope.INSTANCE;

        assertSame(instance1, instance2);
    }

    @Test
    void testClose_DoesNothing() {
        NoopTraceScope scope = NoopTraceScope.INSTANCE;

        assertDoesNotThrow(scope::close);
    }

    @Test
    void testClose_MultipleCalls() {
        NoopTraceScope scope = NoopTraceScope.INSTANCE;

        assertDoesNotThrow(() -> {
            scope.close();
            scope.close();
            scope.close();
        });
    }

    @SuppressWarnings("EmptyTryBlock")
    @Test
    void testClose_AsAutoCloseable() {
        try (TraceScope ignored = NoopTraceScope.INSTANCE) {

        } catch (Exception e) {
            fail("Should not throw exception");
        }
    }
}

