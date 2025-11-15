package id.xtramile.flexretry.strategy.policy;

import id.xtramile.flexretry.support.time.Clock;

public interface RetryWindow {
    static RetryWindow always() {
        return clock -> true;
    }

    boolean allowedNow(Clock clock);
}
