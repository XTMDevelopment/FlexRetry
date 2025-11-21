module id.xtramile.flexretry.control {
    // Dependencies
    requires transitive id.xtramile.flexretry.core;

    // Public API exports
    exports id.xtramile.flexretry.control;
    exports id.xtramile.flexretry.control.batch;
    exports id.xtramile.flexretry.control.breaker;
    exports id.xtramile.flexretry.control.budget;
    exports id.xtramile.flexretry.control.bulkhead;
    exports id.xtramile.flexretry.control.cache;
    exports id.xtramile.flexretry.control.concurrency;
    exports id.xtramile.flexretry.control.health;
    exports id.xtramile.flexretry.control.ratelimit;
    exports id.xtramile.flexretry.control.registry;
    exports id.xtramile.flexretry.control.sf;
    exports id.xtramile.flexretry.control.stats;
    exports id.xtramile.flexretry.control.tuning;
}