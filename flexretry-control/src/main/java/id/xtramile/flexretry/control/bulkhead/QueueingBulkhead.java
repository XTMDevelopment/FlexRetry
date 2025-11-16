package id.xtramile.flexretry.control.bulkhead;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public final class QueueingBulkhead {
    private final Semaphore semaphore;

    public QueueingBulkhead(int maxConcurrent) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("maxConcurrent >= 1");
        }

        this.semaphore = new Semaphore(maxConcurrent, true);
    }

    public boolean tryAcquire(long timeoutMillis) throws InterruptedException {
        return semaphore.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public void release() {
        semaphore.release();
    }
}
