package id.xtramile.flexretry.observability.events;

public enum RetryEventType {
    RETRY_SCHEDULED,
    RETRY_ATTEMPT,
    RETRY_SUCCESS,
    RETRY_FAILURE,
    RETRY_GIVE_UP
}
