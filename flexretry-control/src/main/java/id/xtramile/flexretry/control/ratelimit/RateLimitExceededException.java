package id.xtramile.flexretry.control.ratelimit;

public final class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}

