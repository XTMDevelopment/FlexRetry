package id.xtramile.flexretry.control.breaker;

public interface FailureAccrualPolicy {
    boolean recordSuccess();
    boolean recordFailure();
    boolean isTripped();
    void reset();
}
