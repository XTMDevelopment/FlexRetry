package id.xtramile.flexretry.observability.events;

import id.xtramile.flexretry.RetryContext;
import id.xtramile.flexretry.RetryOutcome;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class RetryEvent<T> {
    private final RetryEventType type;
    private final RetryContext<T> context;
    private final int attempt;
    private final Throwable lastError;
    private final Duration nextDelay;
    private final RetryOutcome<T> outcome;
    private final Instant timestamp;

    private RetryEvent(Builder<T> builder) {
        this.type = Objects.requireNonNull(builder.type, "type");
        this.context = builder.context;
        this.attempt = builder.attempt;
        this.lastError = builder.lastError;
        this.nextDelay = builder.nextDelay;
        this.outcome = builder.outcome;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
    }

    public RetryEventType getType() {
        return type;
    }

    public RetryContext<T> getContext() {
        return context;
    }

    public int getAttempt() {
        return attempt;
    }

    public Throwable getLastError() {
        return lastError;
    }

    public Duration getNextDelay() {
        return nextDelay;
    }

    public RetryOutcome<T> getOutcome() {
        return outcome;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public static <T> Builder<T> builder(RetryEventType type) {
        return new Builder<>(type);
    }

    public static final class Builder<T> {
        private final RetryEventType type;
        private RetryContext<T> context;
        private int attempt;
        private Throwable lastError;
        private Duration nextDelay;
        private RetryOutcome<T> outcome;
        private Instant timestamp;

        public Builder(RetryEventType type) {
            this.type = type;
        }

        public Builder<T> context(RetryContext<T> context) {
            this.context = context;
            return this;
        }

        public Builder<T> attempt(int attempt) {
            this.attempt = attempt;
            return this;
        }

        public Builder<T> lastError(Throwable lastError) {
            this.lastError = lastError;
            return this;
        }

        public Builder<T> nextDelay(Duration nextDelay) {
            this.nextDelay = nextDelay;
            return this;
        }

        public Builder<T> outcome(RetryOutcome<T> outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder<T> timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public RetryEvent<T> build() {
            return new RetryEvent<>(this);
        }
    }
}
