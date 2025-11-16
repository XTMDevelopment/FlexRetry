package id.xtramile.flexretry.control.stats;

import java.time.Duration;
import java.util.ArrayDeque;

public final class SlidingWindowStats {
    private final ArrayDeque<Long> successes = new ArrayDeque<>();
    private final ArrayDeque<Long> failures = new ArrayDeque<>();
    private final long windowNanos;

    public SlidingWindowStats(Duration window) {
        this.windowNanos = window.toNanos();
    }

    public synchronized void recordSuccess() {
        successes.addLast(System.nanoTime());
        purge();
    }

    public synchronized void recordFailure() {
        failures.addLast(System.nanoTime());
        purge();
    }

    public synchronized int successes() {
        purge();
        return successes.size();
    }

    public synchronized int failures() {
        purge();
        return failures.size();
    }

    private void purge() {
        long cutoff = System.nanoTime() - windowNanos;

        while (!successes.isEmpty() && successes.peekFirst() < cutoff) {
            successes.removeFirst();
        }

        while (!failures.isEmpty() && failures.peekFirst() < cutoff) {
            failures.removeFirst();
        }
    }
}
