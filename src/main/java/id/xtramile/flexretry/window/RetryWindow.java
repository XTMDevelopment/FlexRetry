package id.xtramile.flexretry.window;

import id.xtramile.flexretry.time.Clock;

public interface RetryWindow {
    boolean allowedNow(Clock clock);

    static RetryWindow always() {
        return clock -> true;
    }
}
