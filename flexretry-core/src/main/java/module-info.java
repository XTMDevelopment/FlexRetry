module id.xtramile.flexretry.core {
    exports id.xtramile.flexretry;
    exports id.xtramile.flexretry.config;
    exports id.xtramile.flexretry.lifecycle;
    exports id.xtramile.flexretry.strategy.backoff;
    exports id.xtramile.flexretry.strategy.policy;
    exports id.xtramile.flexretry.strategy.stop;
    exports id.xtramile.flexretry.strategy.timeout;
    exports id.xtramile.flexretry.support.rand;
    exports id.xtramile.flexretry.support.time;

    opens id.xtramile.flexretry to id.xtramile.flexretry.observability;
}