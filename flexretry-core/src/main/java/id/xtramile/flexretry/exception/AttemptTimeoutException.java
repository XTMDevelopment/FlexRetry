package id.xtramile.flexretry.exception;

public class AttemptTimeoutException extends RuntimeException {
    public AttemptTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
