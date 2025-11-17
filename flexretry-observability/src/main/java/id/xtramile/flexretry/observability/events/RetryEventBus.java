package id.xtramile.flexretry.observability.events;

public interface RetryEventBus<T> {
    void publish(RetryEvent<T> event);
    void register(RetryEventListener<T> listener);
    void unregister(RetryEventListener<T> listener);

    @SuppressWarnings("unchecked")
    static <T> RetryEventBus<T> noop() {
        return (RetryEventBus<T>) NoopRetryEventBus.INSTANCE;
    }
}
