package id.xtramile.flexretry.tuning;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableTuning {
    private final AtomicInteger maxAttempts = new AtomicInteger(3);
    private final AtomicReference<Duration> maxElapsed = new AtomicReference<>(null);

    public int maxAttempts() {
        return maxAttempts.get();
    }

    public void setMaxAttempts(int value) {
        maxAttempts.set(value);
    }

    public Duration maxElapsed() {
        return maxElapsed.get();
    }

    public void setMaxElapsed(Duration value) {
        maxElapsed.set(value);
    }
}
