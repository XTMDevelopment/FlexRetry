package id.xtramile.flexretry.control.budget;

public interface RetryBudget {
    static RetryBudget unlimited() {
        return () -> true;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean tryAcquire();
}
