package id.xtramile.flexretry.control.concurrency;

public interface ConcurrencyLimiter {
    boolean tryAcquire();
    void onSuccess();
    void onDropped();
}
