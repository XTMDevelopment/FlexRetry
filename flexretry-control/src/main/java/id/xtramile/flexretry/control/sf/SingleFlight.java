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
            return execute(key, task);
        }

        if (future.getNumberOfDependents() > 0) {
            try {
                return future.get();

            } catch (ExecutionException ee) {
                Throwable cause = ee.getCause() == null ? ee : ee.getCause();

                if (cause instanceof Exception) {
                    throw (Exception) cause;
                } else if (cause instanceof Error) {
                    throw (Error) cause;
                }

                throw new RuntimeException(cause);
            }
        }

        try {
            T result = task.call();
            future.complete(result);
            return result;

        } catch (Throwable t) {
            future.completeExceptionally(t);

            if (t instanceof Exception) {
                throw (Exception) t;
            } else {
                throw (Error) t;
            }

        } finally {
            inflight.remove(key, future);
        }
    }
}
