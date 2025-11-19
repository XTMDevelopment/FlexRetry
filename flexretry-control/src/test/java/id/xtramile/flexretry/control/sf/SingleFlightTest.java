package id.xtramile.flexretry.control.sf;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SingleFlightTest {

    @Test
    void testExecute_SingleCall() throws Exception {
        SingleFlight<String> sf = new SingleFlight<>();
        AtomicInteger callCount = new AtomicInteger(0);
        
        String result = sf.execute("key1", () -> {
            callCount.incrementAndGet();
            return "result";
        });
        
        assertEquals("result", result);
        assertEquals(1, callCount.get());
    }

    @Test
    void testExecute_DeduplicatesConcurrentCalls() throws Exception {
        SingleFlight<String> sf = new SingleFlight<>();
        AtomicInteger callCount = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(5);
        
        String[] results = new String[5];
        Thread[] threads = new Thread[5];
        
        for (int i = 0; i < 5; i++) {
            final int index = i;

            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();

                    results[index] = sf.execute("key1", () -> {
                        callCount.incrementAndGet();

                        try {
                            Thread.sleep(100);

                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }

                        return "result";
                    });

                } catch (Exception e) {
                    e.printStackTrace();

                } finally {
                    doneLatch.countDown();
                }
            });

            threads[i].start();
        }
        
        Thread.sleep(10);
        startLatch.countDown();
        doneLatch.await();

        for (String result : results) {
            assertEquals("result", result);
        }

        assertTrue(callCount.get() >= 1 && callCount.get() <= 5, 
            "Call count should be between 1 and 5, but was " + callCount.get());
    }

    @Test
    void testExecute_DifferentKeys() throws Exception {
        SingleFlight<String> sf = new SingleFlight<>();
        AtomicInteger callCount = new AtomicInteger(0);
        
        String result1 = sf.execute("key1", () -> {
            callCount.incrementAndGet();
            return "result1";
        });
        
        String result2 = sf.execute("key2", () -> {
            callCount.incrementAndGet();
            return "result2";
        });
        
        assertEquals("result1", result1);
        assertEquals("result2", result2);
        assertEquals(2, callCount.get());
    }

    @Test
    void testExecute_ExceptionPropagates() {
        SingleFlight<String> sf = new SingleFlight<>();
        
        assertThrows(RuntimeException.class,
                () -> sf.execute("key1", () -> {
                    throw new RuntimeException("test error");
        }));
    }

    @Test
    void testExecute_ExceptionPropagatesToAllWaiters() throws Exception {
        SingleFlight<String> sf = new SingleFlight<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(5);
        
        Exception[] exceptions = new Exception[5];
        Thread[] threads = new Thread[5];
        
        for (int i = 0; i < 5; i++) {
            final int index = i;

            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();

                    sf.execute("key1", () -> {
                        throw new RuntimeException("test error");
                    });

                } catch (Exception e) {
                    exceptions[index] = e;

                } finally {
                    doneLatch.countDown();
                }
            });

            threads[i].start();
        }
        
        startLatch.countDown();
        doneLatch.await();

        for (Exception exception : exceptions) {
            assertNotNull(exception);
            assertInstanceOf(RuntimeException.class, exception);
            assertEquals("test error", exception.getMessage());
        }
    }

    @Test
    void testExecute_AfterCompletion() throws Exception {
        SingleFlight<String> sf = new SingleFlight<>();
        
        String result1 = sf.execute("key1", () -> "result1");
        assertEquals("result1", result1);

        String result2 = sf.execute("key1", () -> "result2");
        assertEquals("result2", result2);
    }

    @Test
    void testExecute_CheckedException() {
        SingleFlight<String> sf = new SingleFlight<>();
        
        assertThrows(Exception.class,
                () -> sf.execute("key1", () -> {
                    throw new Exception("checked exception");
        }));
    }

    @Test
    void testExecute_ErrorPropagates() {
        SingleFlight<String> sf = new SingleFlight<>();
        
        assertThrows(Error.class,
                () -> sf.execute("key1", () -> {
                    throw new Error("test error");
        }));
    }

    @Test
    void testExecute_ErrorPropagatesToAllWaiters() throws Exception {
        SingleFlight<String> sf = new SingleFlight<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(5);
        
        Throwable[] errors = new Throwable[5];
        Thread[] threads = new Thread[5];
        
        for (int i = 0; i < 5; i++) {
            final int index = i;

            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();

                    sf.execute("key1", () -> {
                        throw new Error("test error");
                    });

                } catch (Throwable e) {
                    errors[index] = e;

                } finally {
                    doneLatch.countDown();
                }
            });

            threads[i].start();
        }
        
        startLatch.countDown();
        doneLatch.await();

        for (Throwable error : errors) {
            assertNotNull(error);
            assertInstanceOf(Error.class, error);
            assertEquals("test error", error.getMessage());
        }
    }

    @Test
    void testExecute_NullReturnValue() throws Exception {
        SingleFlight<String> sf = new SingleFlight<>();
        
        String result = sf.execute("key1", () -> null);
        assertNull(result);
    }

    @Test
    void testExecute_NullReturnValueWithConcurrentCalls() throws Exception {
        SingleFlight<String> sf = new SingleFlight<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(5);
        
        String[] results = new String[5];
        Thread[] threads = new Thread[5];
        
        for (int i = 0; i < 5; i++) {
            final int index = i;

            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();

                    results[index] = sf.execute("key1", () -> null);

                } catch (Exception e) {
                    e.printStackTrace();

                } finally {
                    doneLatch.countDown();
                }
            });

            threads[i].start();
        }
        
        Thread.sleep(10);
        startLatch.countDown();
        doneLatch.await();

        for (String result : results) {
            assertNull(result);
        }
    }

    @Test
    void testExecute_ReusesCompletedFuture() throws Exception {
        SingleFlight<String> sf = new SingleFlight<>();
        AtomicInteger callCount = new AtomicInteger(0);
        
        // First call completes
        String result1 = sf.execute("key1", () -> {
            callCount.incrementAndGet();
            return "result1";
        });
        assertEquals("result1", result1);
        assertEquals(1, callCount.get());
        
        String result2 = sf.execute("key1", () -> {
            callCount.incrementAndGet();
            return "result2";
        });
        assertEquals("result2", result2);
        assertEquals(2, callCount.get());
    }

    @Test
    void testExecute_InterruptedException() throws Exception {
        SingleFlight<String> sf = new SingleFlight<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(1);
        
        Exception[] exceptions = new Exception[1];
        Thread thread = new Thread(() -> {
            try {
                startLatch.await();
                
                sf.execute("key1", () -> {
                    Thread.currentThread().interrupt();
                    Thread.sleep(100);
                    return "result";
                });
            } catch (Exception e) {
                exceptions[0] = e;
            } finally {
                doneLatch.countDown();
            }
        });
        
        thread.start();
        startLatch.countDown();
        doneLatch.await();
        
        assertNotNull(exceptions[0]);
    }

    @Test
    void testExecute_ExecutionExceptionWithNullCause() throws Exception {
        SingleFlight<String> sf = new SingleFlight<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        
        Exception[] exceptions = new Exception[2];
        Thread[] threads = new Thread[2];
        
        for (int i = 0; i < 2; i++) {
            final int index = i;

            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();

                    sf.execute("key1", () -> {
                        throw new RuntimeException("test");
                    });

                } catch (Exception e) {
                    exceptions[index] = e;

                } finally {
                    doneLatch.countDown();
                }
            });

            threads[i].start();
        }
        
        startLatch.countDown();
        doneLatch.await();

        for (Exception exception : exceptions) {
            assertNotNull(exception);
            assertInstanceOf(RuntimeException.class, exception);
        }
    }

    @Test
    void testExecute_MultipleKeysConcurrently() throws Exception {
        SingleFlight<String> sf = new SingleFlight<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(10);
        
        AtomicInteger callCount = new AtomicInteger(0);
        String[] results = new String[10];
        Thread[] threads = new Thread[10];
        
        for (int i = 0; i < 10; i++) {
            final int index = i;
            final String key = "key" + (i % 3);

            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();

                    results[index] = sf.execute(key, () -> {
                        callCount.incrementAndGet();
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "result-" + key;
                    });

                } catch (Exception e) {
                    e.printStackTrace();

                } finally {
                    doneLatch.countDown();
                }
            });

            threads[i].start();
        }
        
        Thread.sleep(10);
        startLatch.countDown();
        doneLatch.await();

        assertTrue(callCount.get() >= 3 && callCount.get() <= 10, 
            "Call count should be between 3 and 10, but was " + callCount.get());
        
        for (int i = 0; i < 10; i++) {
            String key = "key" + (i % 3);
            assertEquals("result-" + key, results[i]);
        }
    }

    @Test
    void testExecute_ExceptionAfterCompletion() {
        SingleFlight<String> sf = new SingleFlight<>();
        
        try {
            String result = sf.execute("key1", () -> "success");
            assertEquals("success", result);

        } catch (Exception e) {
            fail("First call should succeed");
        }
        
        assertThrows(RuntimeException.class,
                () -> sf.execute("key1", () -> {
                    throw new RuntimeException("error");
        }));
    }

    @Test
    void testExecute_ConcurrentExceptionAndSuccess() throws Exception {
        SingleFlight<String> sf = new SingleFlight<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        String[] results = new String[2];
        
        Thread thread1 = new Thread(() -> {
            try {
                startLatch.await();
                results[0] = sf.execute("key1", () -> {
                    successCount.incrementAndGet();

                    try {
                        Thread.sleep(50);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    return "success";
                });

            } catch (Exception e) {
                errorCount.incrementAndGet();

            } finally {
                doneLatch.countDown();
            }
        });
        
        Thread thread2 = new Thread(() -> {
            try {
                startLatch.await();
                results[1] = sf.execute("key1", () -> {
                    successCount.incrementAndGet();

                    try {
                        Thread.sleep(50);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    return "success";
                });

            } catch (Exception e) {
                errorCount.incrementAndGet();

            } finally {
                doneLatch.countDown();
            }
        });
        
        thread1.start();
        thread2.start();
        Thread.sleep(10);
        startLatch.countDown();
        doneLatch.await();
        
        assertEquals("success", results[0]);
        assertEquals("success", results[1]);
        assertTrue(successCount.get() >= 1 && successCount.get() <= 2);
        assertEquals(0, errorCount.get());
    }

    @Test
    void testExecute_EmptyKey() throws Exception {
        SingleFlight<String> sf = new SingleFlight<>();
        
        String result = sf.execute("", () -> "empty");
        assertEquals("empty", result);
    }

    @Test
    void testExecute_NullKey() {
        SingleFlight<String> sf = new SingleFlight<>();
        
        assertThrows(NullPointerException.class,
                () -> sf.execute(null, () -> "null"));
    }
}

