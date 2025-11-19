package id.xtramile.flexretry.control.concurrency;

public final class ConcurrencyLimitedException extends RuntimeException {
    public ConcurrencyLimitedException(String message) {
        super(message);
    }
}

