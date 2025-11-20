package id.xtramile.flexretry.control.tuning;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ReloadableSupplierTest {

    @Test
    void testConstructor() {
        ReloadableSupplier<String> supplier = new ReloadableSupplier<>(() -> "value");
        
        assertEquals("value", supplier.get());
    }

    @Test
    void testConstructor_NullLoader() {
        assertThrows(NullPointerException.class,
                () -> new ReloadableSupplier<>(null));
    }

    @Test
    void testGet() {
        ReloadableSupplier<String> supplier = new ReloadableSupplier<>(() -> "value");
        
        assertEquals("value", supplier.get());
        assertEquals("value", supplier.get());
    }

    @Test
    void testReload() {
        AtomicInteger counter = new AtomicInteger(0);
        ReloadableSupplier<Integer> supplier = new ReloadableSupplier<>(counter::incrementAndGet);
        
        assertEquals(1, supplier.get());
        assertEquals(1, supplier.get());
        
        supplier.reload();
        assertEquals(2, supplier.get());
        assertEquals(2, supplier.get());
    }

    @Test
    void testReload_MultipleTimes() {
        AtomicInteger counter = new AtomicInteger(0);
        ReloadableSupplier<Integer> supplier = new ReloadableSupplier<>(counter::incrementAndGet);
        
        assertEquals(1, supplier.get());
        
        supplier.reload();
        assertEquals(2, supplier.get());
        
        supplier.reload();
        assertEquals(3, supplier.get());
        
        supplier.reload();
        assertEquals(4, supplier.get());
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        ReloadableSupplier<Integer> supplier = new ReloadableSupplier<>(counter::incrementAndGet);
        
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        Integer[] results = new Integer[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;

            threads[i] = new Thread(() -> results[index] = supplier.get());
            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }

        for (Integer result : results) {
            assertEquals(1, result);
        }
    }
}

