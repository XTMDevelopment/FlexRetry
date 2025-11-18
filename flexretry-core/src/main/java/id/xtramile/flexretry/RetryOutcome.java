package id.xtramile.flexretry;

public final class RetryOutcome<T> {
    private final boolean success;
    private final T result;
    private final Throwable error;
    private final int attempts;
    private final String message;

    public RetryOutcome(boolean success, T result, Throwable error, int attempts) {
        this(success, result, error, attempts, null);
    }

    public RetryOutcome(boolean success, T result, Throwable error, int attempts, String message) {
        this.success = success;
        this.result = result;
        this.error = error;
        this.attempts = attempts;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public T result() {
        return result;
    }

    public Throwable error() {
        return error;
    }

    public int attempts() {
        return attempts;
    }

    public String message() {
        return message;
    }
}
