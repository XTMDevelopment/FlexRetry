package id.xtramile.flexretry.control.batch;

import java.util.List;

public final class BatchResult<T> {
    public final List<T> successes;
    public final List<T> failures;

    public BatchResult(List<T> successes, List<T> failures) {
        this.successes = successes;
        this.failures = failures;
    }

    public boolean isComplete() {
        return failures == null || failures.isEmpty();
    }
}
