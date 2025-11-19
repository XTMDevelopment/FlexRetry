package id.xtramile.flexretry.support.rand;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RandomSourceTest {

    @Test
    void testThreadLocal() {
        RandomSource rnd = RandomSource.threadLocal();
        assertNotNull(rnd);

        for (int i = 0; i < 10; i++) {
            long value = rnd.nextLong(0, 100);
            assertTrue(value >= 0 && value < 100);
        }
    }

    @Test
    void testThreadLocalWithDifferentRanges() {
        RandomSource rnd = RandomSource.threadLocal();
        
        long value1 = rnd.nextLong(0, 10);
        assertTrue(value1 >= 0 && value1 < 10);
        
        long value2 = rnd.nextLong(100, 200);
        assertTrue(value2 >= 100 && value2 < 200);
    }
}

