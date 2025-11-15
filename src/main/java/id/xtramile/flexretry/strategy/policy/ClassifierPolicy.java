package id.xtramile.flexretry.strategy.policy;

import java.util.Objects;

public final class ClassifierPolicy<T> implements RetryPolicy<T> {
    public enum Decision {
        SUCCESS,
        RETRY,
        FAIL
    }

    public interface ResultClassifier<T> {
        Decision decide(T result);
    }

    private final ResultClassifier<T> classifier;

    public ClassifierPolicy(ResultClassifier<T> classifier) {
        this.classifier = Objects.requireNonNull(classifier, "classifier");
    }

    @Override
    public boolean shouldRetry(T result, Throwable error, int attempt, int maxAttempts) {
        if (error != null) {
            return false;
        }

        if (attempt >= maxAttempts) {
            return false;
        }

        return classifier.decide(result) == Decision.RETRY;
    }
}
