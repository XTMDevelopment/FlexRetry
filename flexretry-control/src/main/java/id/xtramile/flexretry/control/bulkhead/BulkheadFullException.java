package id.xtramile.flexretry.control.bulkhead;

public final class BulkheadFullException extends RuntimeException {
    public BulkheadFullException(String message) {
        super(message);
    }
}
