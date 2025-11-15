package id.xtramile.flexretry.events;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class RetryEventBus<T> {
    private final List<Consumer<RetryEvent<T>>> subscribers = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<RetryEvent<T>> consumer) {
        subscribers.add(consumer);
    }

    public void publish(RetryEvent<T> event) {
        for (Consumer<RetryEvent<T>> consumer : subscribers) {
            try {
                consumer.accept(event);

            } catch (Throwable ignore) {}
        }
    }
}
