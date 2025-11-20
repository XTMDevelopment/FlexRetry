package id.xtramile.flexretry.control.breaker;

import java.time.Duration;

public final class CircuitBreaker {
    private final FailureAccrualPolicy policy;
    private final long openNanos;
    private volatile CircuitBreakerState state = CircuitBreakerState.CLOSED;
    private volatile long openedAt = 0L;
    private final Object lock = new Object();

    public CircuitBreaker(FailureAccrualPolicy policy, Duration openFor) {
        this.policy = policy;
        this.openNanos = openFor.toNanos();
    }

    public boolean allow() {
        CircuitBreakerState currentState = state;
        
        if (currentState == CircuitBreakerState.CLOSED) {
            return true;
        }

        if (currentState == CircuitBreakerState.OPEN) {
            synchronized (lock) {
                if (state == CircuitBreakerState.OPEN) {
                    long now = System.nanoTime();
                    if (now - openedAt >= openNanos) {
                        state = CircuitBreakerState.HALF_OPEN;
                        return true;
                    }
                }
                return state != CircuitBreakerState.OPEN;
            }
        }

        return true;
    }

    public void onSuccess() {
        policy.recordSuccess();

        synchronized (lock) {
            if (state != CircuitBreakerState.CLOSED) {
                state = CircuitBreakerState.CLOSED;
                policy.reset();
            }
        }
    }

    public void onFailure() {
        if (policy.recordFailure() && policy.isTripped()) {
            synchronized (lock) {
                if (state != CircuitBreakerState.OPEN) {
                    state = CircuitBreakerState.OPEN;
                    openedAt = System.nanoTime();
                }
            }
        }
    }
}
