package id.xtramile.flexretry.tuning;

import java.util.concurrent.atomic.AtomicBoolean;

public final class RetrySwitch {
    private final AtomicBoolean on = new AtomicBoolean(true);

    public boolean isOn() {
        return on.get();
    }

    public void setOn(boolean value) {
        on.set(value);
    }
}
