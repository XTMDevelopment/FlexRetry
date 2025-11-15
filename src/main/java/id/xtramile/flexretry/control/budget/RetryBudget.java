package id.xtramile.flexretry.control.budget;

public interface RetryBudget {
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean tryAcquire();

    static RetryBudget unlimited() {
        return () -> true;
    }
}
