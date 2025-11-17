package id.xtramile.flexretry.observability.events;

public final class NoopRetryEventBus<T> implements RetryEventBus<T> {
    public static final NoopRetryEventBus<?> INSTANCE = new NoopRetryEventBus<>();

    private NoopRetryEventBus() {
    }

    @Override
    public void publish(RetryEvent<T> event) {
    }

    @Override
    public void register(RetryEventListener<T> listener) {
    }

    @Override
    public void unregister(RetryEventListener<T> listener) {
    }
}
