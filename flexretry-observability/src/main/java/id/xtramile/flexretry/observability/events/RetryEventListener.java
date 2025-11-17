package id.xtramile.flexretry.observability.events;

@FunctionalInterface
public interface RetryEventListener<T> {
    void onEvent(RetryEvent<T> event);
}
