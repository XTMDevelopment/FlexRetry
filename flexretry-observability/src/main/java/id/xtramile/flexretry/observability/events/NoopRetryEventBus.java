package id.xtramile.flexretry.observability.events;

public final class NoopRetryEventBus implements RetryEventBus<Object> {
    public static final NoopRetryEventBus INSTANCE = new NoopRetryEventBus();

    private NoopRetryEventBus() {
    }

    @Override
    public void publish(RetryEvent<Object> event) {
    }

    @Override
    public void register(RetryEventListener<Object> listener) {
    }

    @Override
    public void unregister(RetryEventListener<Object> listener) {
    }
}
