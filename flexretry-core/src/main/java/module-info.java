module id.xtramile.flexretry.core {
    // Core API exports
    exports id.xtramile.flexretry;
    exports id.xtramile.flexretry.config;
    exports id.xtramile.flexretry.exception;
    exports id.xtramile.flexretry.lifecycle;

    // Strategy exports
    exports id.xtramile.flexretry.strategy.backoff;
    exports id.xtramile.flexretry.strategy.policy;
    exports id.xtramile.flexretry.strategy.stop;
    exports id.xtramile.flexretry.strategy.timeout;

    // Support exports
    exports id.xtramile.flexretry.support.rand;
    exports id.xtramile.flexretry.support.time;

    // Reflection access for observability module
    opens id.xtramile.flexretry to id.xtramile.flexretry.observability;
    opens id.xtramile.flexretry.exception to id.xtramile.flexretry.observability;
}