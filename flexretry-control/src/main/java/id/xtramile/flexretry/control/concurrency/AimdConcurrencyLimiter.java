package id.xtramile.flexretry.control.concurrency;

import java.util.concurrent.atomic.AtomicInteger;

public final class AimdConcurrencyLimiter implements ConcurrencyLimiter {
    private final int max;
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private int limit;

    public AimdConcurrencyLimiter(int initial, int max) {
        if (initial < 1) {
            throw new IllegalArgumentException("initial >= 1");
        }

        if (max < initial) {
            throw new IllegalArgumentException("max >= initial");
        }

        this.limit = initial;
        this.max = max;
    }

    @Override
    public boolean tryAcquire() {
        for (;;) {
            int current = inFlight.get();

            if (current >= limit) {
                return false;
            }

            if (inFlight.compareAndSet(current, current+1)) {
                return true;
            }
        }
    }

    @Override
    public void onSuccess() {
        inFlight.decrementAndGet();

        if (limit < max) {
            limit++;
        }
    }

    @Override
    public void onDropped() {
        inFlight.decrementAndGet();
        limit = Math.max(1, limit / 2);
    }
}
