package id.xtramile.flexretry.observability.trace;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Simple customizable trace scope implementation that executes callbacks on close.
 */
public final class SimpleTraceScope implements TraceScope {
    private final Runnable onClose;
    private final Consumer<Exception> onCloseError;

    private SimpleTraceScope(Runnable onClose, Consumer<Exception> onCloseError) {
        this.onClose = onClose;
        this.onCloseError = onCloseError;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SimpleTraceScope create(Runnable onClose) {
        return new SimpleTraceScope(onClose, null);
    }

    public static SimpleTraceScope create(Runnable onClose, Consumer<Exception> onCloseError) {
        return new SimpleTraceScope(onClose, onCloseError);
    }

    @Override
    public void close() {
        try {
            if (onClose != null) {
                onClose.run();
            }

        } catch (Exception e) {
            if (onCloseError != null) {
                onCloseError.accept(e);
            } else {
                throw e;
            }
        }
    }

    public static final class Builder {
        private Runnable onClose;
        private Consumer<Exception> onCloseError;

        private Builder() {
        }

        public Builder onClose(Runnable onClose) {
            this.onClose = onClose;
            return this;
        }

        public Builder onCloseError(Consumer<Exception> onCloseError) {
            this.onCloseError = onCloseError;
            return this;
        }

        public SimpleTraceScope build() {
            Objects.requireNonNull(onClose, "onClose");
            return new SimpleTraceScope(onClose, onCloseError);
        }
    }
}

