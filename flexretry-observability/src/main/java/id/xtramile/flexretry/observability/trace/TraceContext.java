package id.xtramile.flexretry.observability.trace;

import java.util.Collections;
import java.util.Map;

public interface TraceContext {
    default TraceScope withSpan(String operationName) {
        return withSpan(operationName, Collections.emptyMap());
    }

    TraceScope withSpan(String operationName, Map<String, String> attributes);

    static TraceContext noop() {

    }
}
