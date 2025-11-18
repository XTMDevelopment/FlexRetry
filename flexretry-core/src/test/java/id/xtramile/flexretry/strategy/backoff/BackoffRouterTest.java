package id.xtramile.flexretry.strategy.backoff;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class BackoffRouterTest {

    @Test
    void testConstructor() {
        BackoffRouter router = new BackoffRouter();
        assertNotNull(router);
    }

    @Test
    void testWhen() {
        BackoffRouter router = new BackoffRouter();
        Predicate<Throwable> predicate = e -> e instanceof RuntimeException;
        BackoffStrategy backoff = new FixedBackoff(Duration.ofMillis(100));
        
        BackoffRouter result = router.when(predicate, backoff);
        assertSame(router, result);
    }

    @Test
    void testWhenWithNullPredicate() {
        BackoffRouter router = new BackoffRouter();
        BackoffStrategy backoff = new FixedBackoff(Duration.ofMillis(100));
        
        assertThrows(NullPointerException.class, () -> router.when(null, backoff));
    }

    @Test
    void testWhenWithNullBackoff() {
        BackoffRouter router = new BackoffRouter();
        Predicate<Throwable> predicate = e -> e instanceof RuntimeException;
        
        assertThrows(NullPointerException.class, () -> router.when(predicate, null));
    }

    @Test
    void testDefaultTo() {
        BackoffRouter router = new BackoffRouter();
        BackoffStrategy backoff = new FixedBackoff(Duration.ofMillis(200));
        
        BackoffRouter result = router.defaultTo(backoff);
        assertSame(router, result);
    }

    @Test
    void testDefaultToWithNull() {
        BackoffRouter router = new BackoffRouter();
        
        assertThrows(NullPointerException.class, () -> router.defaultTo(null));
    }

    @Test
    void testSelectWithMatchingRoute() {
        BackoffRouter router = new BackoffRouter();
        BackoffStrategy errorBackoff = new FixedBackoff(Duration.ofMillis(100));
        router.when(e -> e instanceof RuntimeException, errorBackoff);
        
        RuntimeException error = new RuntimeException("error");
        BackoffStrategy selected = router.select(error);
        
        assertEquals(errorBackoff, selected);
    }

    @Test
    void testSelectWithNonMatchingRoute() {
        BackoffRouter router = new BackoffRouter();
        BackoffStrategy errorBackoff = new FixedBackoff(Duration.ofMillis(100));
        router.when(e -> e instanceof IllegalArgumentException, errorBackoff);
        
        RuntimeException error = new RuntimeException("error");
        BackoffStrategy selected = router.select(error);

        assertNotNull(selected);
        assertNotEquals(errorBackoff, selected);
    }

    @Test
    void testSelectWithNullError() {
        BackoffRouter router = new BackoffRouter();
        BackoffStrategy errorBackoff = new FixedBackoff(Duration.ofMillis(100));
        router.when(e -> e instanceof RuntimeException, errorBackoff);
        
        BackoffStrategy selected = router.select(null);

        assertNotNull(selected);
    }

    @Test
    void testSelectWithMultipleRoutes() {
        BackoffRouter router = new BackoffRouter();
        BackoffStrategy runtimeBackoff = new FixedBackoff(Duration.ofMillis(100));
        BackoffStrategy illegalBackoff = new FixedBackoff(Duration.ofMillis(200));
        
        router.when(e -> e instanceof IllegalArgumentException, illegalBackoff);
        router.when(e -> e instanceof RuntimeException, runtimeBackoff);
        
        RuntimeException error1 = new RuntimeException("error");
        IllegalArgumentException error2 = new IllegalArgumentException("error");

        assertEquals(illegalBackoff, router.select(error2));
        assertEquals(runtimeBackoff, router.select(error1));
    }

    @Test
    void testSelectWithFirstMatchingRoute() {
        BackoffRouter router = new BackoffRouter();
        BackoffStrategy firstBackoff = new FixedBackoff(Duration.ofMillis(100));
        BackoffStrategy secondBackoff = new FixedBackoff(Duration.ofMillis(200));
        
        router.when(e -> e instanceof RuntimeException, firstBackoff);
        router.when(e -> e instanceof RuntimeException, secondBackoff);
        
        RuntimeException error = new RuntimeException("error");
        BackoffStrategy selected = router.select(error);

        assertEquals(firstBackoff, selected);
    }

    @Test
    void testSelectWithDefault() {
        BackoffRouter router = new BackoffRouter();
        BackoffStrategy defaultBackoff = new FixedBackoff(Duration.ofMillis(300));
        router.defaultTo(defaultBackoff);
        
        RuntimeException error = new RuntimeException("error");
        BackoffStrategy selected = router.select(error);
        
        assertEquals(defaultBackoff, selected);
    }
}

