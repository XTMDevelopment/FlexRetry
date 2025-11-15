package id.xtramile.flexretry.policy;

import id.xtramile.flexretry.time.Clock;
import id.xtramile.flexretry.window.RetryWindow;

public final class WindowPolicy<T> implements RetryPolicy<T> {
    private final RetryWindow window;
    private final Clock clock;

    public WindowPolicy(RetryWindow window, Clock clock) {
        this.window = window;
        this.clock = clock;
    }

    @Override
    public boolean shouldRetry(T result, Throwable error, int attempt, int maxAttempts) {
        return window.allowedNow(clock);
    }
}
