package id.xtramile.flexretry;

public final class RetryOutcome<T> {
    private final boolean success;
    private final T result;
    private final Throwable error;
    private final int attempts;

    public RetryOutcome(boolean success, T result, Throwable error, int attempts) {
        this.success = success;
        this.result = result;
        this.error = error;
        this.attempts = attempts;
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
}
