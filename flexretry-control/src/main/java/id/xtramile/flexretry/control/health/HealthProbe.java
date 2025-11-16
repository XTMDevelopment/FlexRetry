package id.xtramile.flexretry.control.health;

public interface HealthProbe {
    State state();

    enum State {
        UP,
        DEGRADED,
        DOWN
    }
}
