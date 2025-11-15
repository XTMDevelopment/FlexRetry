package id.xtramile.flexretry.policy;

import java.util.Objects;
import java.util.function.Predicate;

public final class ExceptionPredicatePolicy<T> implements RetryPolicy<T> {
    private final Predicate<Throwable> predicate;

    public ExceptionPredicatePolicy(Predicate<Throwable> predicate) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
    }

    @Override
    public boolean shouldRetry(T result, Throwable error, int attempt, int maxAttempts) {
        return error != null && attempt < maxAttempts && predicate.test(error);
    }

    public static Predicate<Throwable> byCause(Class<? extends Throwable> type) {
        return e -> {
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (type.isInstance(t)) {
                    return true;
                }
            }

            return false;
        };
    }
}
