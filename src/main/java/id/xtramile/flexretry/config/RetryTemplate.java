package id.xtramile.flexretry.config;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class RetryTemplate<T> {
    private final RetryConfig<T> config;

    public RetryTemplate(RetryConfig<T> config) {
        this.config = config;
    }

    public T run(Callable<T> task) {
        return config.run(task);
    }

    public CompletableFuture<T> runAsync(Callable<T> task, Executor executor) {
        return config.runAsync(task, executor);
    }
}
