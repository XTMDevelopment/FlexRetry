package id.xtramile.flexretry.bulkhead;

import java.util.concurrent.Semaphore;

public final class Bulkhead {
    private final Semaphore sem;

    public Bulkhead(int maxConcurrent) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("maxConcurrent >= 1");
        }

        this.sem = new Semaphore(maxConcurrent, true);
    }

    public boolean tryAcquire() {
        return sem.tryAcquire();
    }

    public void release() {
        sem.release();
    }
}
