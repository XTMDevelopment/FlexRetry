package id.xtramile.flexretry.observability.events;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SimpleRetryEventBus<T> implements RetryEventBus<T> {
    private final List<RetryEventListener<T>> listeners = new CopyOnWriteArrayList<>();

    public static <T> SimpleRetryEventBus<T> create() {
        return new SimpleRetryEventBus<>();
    }

    @Override
    public void publish(RetryEvent<T> event) {
        if (event == null) {
            return;
        }

        for (RetryEventListener<T> listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException ignore) {}
        }
    }

    @Override
    public void register(RetryEventListener<T> listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
    }

    @Override
    public void unregister(RetryEventListener<T> listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }
}
