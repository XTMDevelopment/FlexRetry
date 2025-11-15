package id.xtramile.flexretry.tuning;

import id.xtramile.flexretry.Retry;
import id.xtramile.flexretry.health.HealthProbe;

public interface DynamicTuning {
    void apply(HealthProbe.State state, Retry.Builder<?> builder);
}
