package id.xtramile.flexretry.health;

public interface HealthProbe {
    enum State {
        UP,
        DEGRADED,
        DOWN
    }

    State state();
}
