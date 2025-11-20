package id.xtramile.flexretry.control.sf;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public final class SingleFlight<T> {
    private final Map<String, CompletableFuture<T>> inflight = new ConcurrentHashMap<>();

    public T execute(String key, Callable<T> task) throws Exception {
        CompletableFuture<T> future = inflight.computeIfAbsent(key, k -> new CompletableFuture<>());

        if (future.isDone()) {
            inflight.remove(key, future);
            return execute(key, task);
        }

        CompletableFuture<T> current = inflight.get(key);
        if (current != null && current != future) {
            return getResult(current);
        }

        if (future.isDone()) {
            try {
                return getResult(future);
            } finally {
                inflight.remove(key, future);
            }
        }

        if (inflight.get(key) == future && !future.isDone()) {
            try {
                T result = task.call();
                future.complete(result);
                return result;

            } catch (Throwable t) {
                future.completeExceptionally(t);
                throwException(t);
                return null;

            } finally {
                inflight.remove(key, future);
            }
        } else {
            return getResult(future);
        }
    }

    private T getResult(CompletableFuture<T> future) throws Exception {
        try {
            return future.get();

        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() == null ? ee : ee.getCause();
            throwException(cause);
            return null;
        }
    }

    private static void throwException(Throwable cause) throws Exception {
        if (cause instanceof Exception) {
            throw (Exception) cause;

        } else if (cause instanceof Error) {
            throw (Error) cause;
        }

        throw new RuntimeException(cause);
    }
}
