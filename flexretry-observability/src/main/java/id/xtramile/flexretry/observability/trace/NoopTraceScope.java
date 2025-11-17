package id.xtramile.flexretry.observability.trace;

public final class NoopTraceScope implements TraceScope {
    static final NoopTraceScope INSTANCE = new NoopTraceScope();

    private NoopTraceScope() {
    }

    @Override
    public void close() {
    }
}
