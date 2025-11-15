package id.xtramile.flexretry.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class ExceptionMappingPolicy<T> implements RetryPolicy<T> {
    public enum Decision {
        RETRY,
        FAIL,
        IGNORE
    }

    public static final class Rule {
        final Predicate<Throwable> test;
        final Decision decision;

        Rule(Predicate<Throwable> test, Decision decision) {
            this.test = test;
            this.decision = decision;
        }
    }

    private final List<Rule> rules = new ArrayList<>();
    private Decision otherwise = Decision.IGNORE;

    public ExceptionMappingPolicy<T> when(Predicate<Throwable> predicate, Decision decision) {
        rules.add(new Rule(Objects.requireNonNull(predicate), Objects.requireNonNull(decision)));
        return this;
    }

    public ExceptionMappingPolicy<T> otherwise(Decision decision) {
        this.otherwise = Objects.requireNonNull(decision);
        return this;
    }

    @Override
    public boolean shouldRetry(T result, Throwable error, int attempt, int maxAttempts) {
        if (error == null || attempt >= maxAttempts) {
            return false;
        }

        for (Rule rule : rules) {
            if (rule.test.test(error)) {
                return rule.decision == Decision.RETRY;
            }
        }

        return otherwise == Decision.RETRY;
    }
}
