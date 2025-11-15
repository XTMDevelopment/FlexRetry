package id.xtramile.flexretry.strategy.policy;

import id.xtramile.flexretry.support.time.Clock;

public interface RetryWindow {
    boolean allowedNow(Clock clock);

    static RetryWindow always() {
        return clock -> true;
    }
}
