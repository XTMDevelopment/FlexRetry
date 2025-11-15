package id.xtramile.flexretry.budget;

public interface RetryBudget {
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean tryAcquire();

    static RetryBudget unlimited() {
        return () -> true;
    }
}
