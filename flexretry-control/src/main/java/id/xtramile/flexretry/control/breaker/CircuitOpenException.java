package id.xtramile.flexretry.control.breaker;

public final class CircuitOpenException extends RuntimeException {
    public CircuitOpenException(String message) {
        super(message);
    }
}

