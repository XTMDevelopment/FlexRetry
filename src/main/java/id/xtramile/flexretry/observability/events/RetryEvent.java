package id.xtramile.flexretry.observability.events;

import id.xtramile.flexretry.RetryContext;

import java.io.Serializable;

public interface RetryEvent<T> extends Serializable {
    RetryContext<T> context();

    final class AttemptStarted<T> implements RetryEvent<T> {
        private final RetryContext<T> context;

        public AttemptStarted(RetryContext<T> context) {
            this.context = context;
        }

        @Override
        public RetryContext<T> context() {
            return context;
        }
    }

    final class AttemptSucceeded<T> implements RetryEvent<T> {
        private final RetryContext<T> context;
        private final T result;

        public AttemptSucceeded(RetryContext<T> context, T result) {
            this.context = context;
            this.result = result;
        }

        @Override
        public RetryContext<T> context() {
            return context;
        }

        public T result() {
            return result;
        }
    }

    final class AttemptFailed<T> implements RetryEvent<T> {
        private final RetryContext<T> context;
        private final Throwable error;

        public AttemptFailed(RetryContext<T> context, Throwable error) {
            this.context = context;
            this.error = error;
        }

        @Override
        public RetryContext<T> context() {
            return context;
        }

        public Throwable error() {
            return error;
        }
    }

    final class Exhausted<T> implements RetryEvent<T> {
        private final RetryContext<T> context;
        private final Throwable lastError;

        public Exhausted(RetryContext<T> context, Throwable lastError) {
            this.context = context;
            this.lastError = lastError;
        }

        @Override
        public RetryContext<T> context() {
            return context;
        }

        public Throwable lastError() {
            return lastError;
        }
    }

    final class Recovered<T> implements RetryEvent<T> {
        private final RetryContext<T> context;
        private final T fallback;

        public Recovered(RetryContext<T> context, T fallback) {
            this.context = context;
            this.fallback = fallback;
        }

        @Override
        public RetryContext<T> context() {
            return context;
        }

        public T fallback() {
            return fallback;
        }
    }
}
