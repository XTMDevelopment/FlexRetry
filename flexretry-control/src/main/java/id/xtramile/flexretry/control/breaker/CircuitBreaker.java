package id.xtramile.flexretry.control.breaker;

import java.time.Duration;

public final class CircuitBreaker {
    private final FailureAccrualPolicy policy;
    private final long openNanos;
    private volatile CircuitBreakerState state = CircuitBreakerState.CLOSED;
    private volatile long openedAt = 0L;

    public CircuitBreaker(FailureAccrualPolicy policy, Duration openFor) {
        this.policy = policy;
        this.openNanos = openFor.toNanos();
    }

    public boolean allow() {
        if (state == CircuitBreakerState.CLOSED) {
            return true;
        }

        if (state == CircuitBreakerState.OPEN) {
            if (System.nanoTime() - openedAt >= openNanos) {
                state = CircuitBreakerState.HALF_OPEN;
                return true;
            }

            return false;
        }

        return true;
    }

    public void onSuccess() {
        policy.recordSuccess();

        if (state != CircuitBreakerState.CLOSED) {
            state = CircuitBreakerState.CLOSED;
            policy.reset();
        }
    }

    public void onFailure() {
        if (policy.recordFailure() && policy.isTripped()) {
            state = CircuitBreakerState.OPEN;
            openedAt = System.nanoTime();
        }
    }
}
