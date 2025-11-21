package id.xtramile.flexretry.exception;

/**
 * Thrown when retries are exhausted or result never meets success criteria
 */
public final class RetryException extends RuntimeException {
    private final int attempts;

    public RetryException(String message, Throwable cause, int attempts) {
        super(message, cause);
        this.attempts = attempts;
    }

    public int attempts() {
        return attempts;
    }
}
