package id.xtramile.flexretry.control.health;

public interface HealthProbe {
    enum State {
        UP,
        DEGRADED,
        DOWN
    }

    State state();
}
