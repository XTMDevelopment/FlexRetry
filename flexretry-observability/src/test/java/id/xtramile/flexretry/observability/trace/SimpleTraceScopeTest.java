package id.xtramile.flexretry.observability.trace;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SimpleTraceScopeTest {

    @Test
    void testBuilder() {
        SimpleTraceScope.Builder builder = SimpleTraceScope.builder();

        assertNotNull(builder);
    }

    @Test
    void testCreate_WithOnClose() {
        AtomicInteger closeCount = new AtomicInteger(0);

        SimpleTraceScope scope = SimpleTraceScope.create(closeCount::incrementAndGet);
        assertNotNull(scope);

        scope.close();
        assertEquals(1, closeCount.get());
    }

    @Test
    void testCreate_WithOnCloseAndOnCloseError() {
        AtomicInteger closeCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        SimpleTraceScope scope = SimpleTraceScope.create(
                () -> {
                    closeCount.incrementAndGet();
                    throw new RuntimeException("close error");
                },
                error -> errorCount.incrementAndGet()
        );

        scope.close();
        assertEquals(1, closeCount.get());
        assertEquals(1, errorCount.get());
    }

    @Test
    void testClose_CallsOnClose() {
        AtomicInteger closeCount = new AtomicInteger(0);

        SimpleTraceScope scope = SimpleTraceScope.builder()
                .onClose(closeCount::incrementAndGet)
                .build();

        scope.close();
        assertEquals(1, closeCount.get());
    }

    @Test
    void testClose_WithException_AndOnCloseError_CallsOnCloseError() {
        AtomicInteger closeCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        SimpleTraceScope scope = SimpleTraceScope.builder()
                .onClose(() -> {
                    closeCount.incrementAndGet();
                    throw new RuntimeException("close error");
                })
                .onCloseError(error -> errorCount.incrementAndGet())
                .build();

        scope.close();
        assertEquals(1, closeCount.get());
        assertEquals(1, errorCount.get());
    }

    @Test
    void testClose_WithException_AndNoOnCloseError_ThrowsException() {
        SimpleTraceScope scope = SimpleTraceScope.builder()
                .onClose(() -> {
                    throw new RuntimeException("close error");
                })
                .build();

        assertThrows(RuntimeException.class, scope::close);
    }

    @Test
    void testClose_WithNullOnClose_DoesNotThrow() {
        SimpleTraceScope scope = SimpleTraceScope.create(() -> {
        });

        assertDoesNotThrow(scope::close);
    }

    @Test
    void testClose_MultipleCalls() {
        AtomicInteger closeCount = new AtomicInteger(0);

        SimpleTraceScope scope = SimpleTraceScope.create(closeCount::incrementAndGet);

        scope.close();
        scope.close();
        scope.close();

        assertEquals(3, closeCount.get());
    }

    @Test
    void testBuild_WithNullOnClose_ThrowsException() {
        SimpleTraceScope.Builder builder = SimpleTraceScope.builder();
        assertThrows(NullPointerException.class, builder::build);
    }

    @Test
    void testBuild_WithValidOnClose() {
        SimpleTraceScope scope = SimpleTraceScope.builder()
                .onClose(() -> {
                })
                .build();

        assertNotNull(scope);
    }
}

