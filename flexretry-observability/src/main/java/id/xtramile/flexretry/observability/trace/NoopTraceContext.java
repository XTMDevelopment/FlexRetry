package id.xtramile.flexretry.observability.trace;

import java.util.Map;

public final class NoopTraceContext implements TraceContext {
    static final NoopTraceContext INSTANCE = new NoopTraceContext();

    private NoopTraceContext() {
    }

    @Override
    public TraceScope withSpan(String operationName, Map<String, String> attributes) {
        return NoopTraceScope.INSTANCE;
    }
}
