package id.xtramile.flexretry.time;

public final class ManualClock implements Clock {
    private long nanos;

    public ManualClock() {
        this(0L);
    }

    public ManualClock(long initialNanos) {
        this.nanos = initialNanos;
    }

    @Override
    public long nanoTime() {
        return nanos;
    }

    public ManualClock advanceNanos(long delta) {
        this.nanos += delta;
        return this;
    }

    public ManualClock advanceMillis(long ms) {
        return advanceNanos(ms * 1_000_000);
    }

    public ManualClock setNanos(long ns) {
        this.nanos = ns;
        return this;
    }
}
