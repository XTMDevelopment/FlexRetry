package id.xtramile.flexretry.async;

import java.time.Duration;
import java.util.concurrent.*;

public interface AsyncBridge {
    <T> CompletionStage<T> submit(Callable<T> task, Duration timeout, Executor executor);

    static <T> AsyncBridge defaultBridge() {
        return (task, timeout, executor) -> {
            CompletableFuture<T> completableFuture = new CompletableFuture<>();

            executor.execute(() -> {
                Future<T> future = null;

                try {
                    if (timeout == null) {
                        completableFuture.complete(task.call());

                    } else {
                        ExecutorService executorService = (executor instanceof ExecutorService)
                                ? (ExecutorService) executor
                                : Executors.newCachedThreadPool();

                        future = executorService.submit(task);
                        completableFuture.complete(future.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
                    }

                } catch (Throwable t) {
                    completableFuture.completeExceptionally(t);
                    if (future != null) {
                        future.cancel(true);
                    }
                }
            });

            return completableFuture;
        };
    }
}
