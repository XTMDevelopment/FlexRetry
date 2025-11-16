package id.xtramile.flexretry.control.tuning;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.control.health.HealthProbe;

public interface DynamicTuning {
    void apply(HealthProbe.State state, Retry.Builder<?> builder);
}
