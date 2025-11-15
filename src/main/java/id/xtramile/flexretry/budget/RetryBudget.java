package id.xtramile.flexretry.budget;

public interface RetryBudget {
    boolean tryAcquire();

    static RetryBudget unlimited() {
        return () -> true;
    }
}
