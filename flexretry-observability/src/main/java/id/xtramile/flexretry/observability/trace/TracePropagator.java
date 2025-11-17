package id.xtramile.flexretry.observability.trace;

import java.util.Map;

public interface TracePropagator<C> {
    void inject(Map<String, String> traceContext, C carrier);
    Map<String, String> extract(C carrier);
}
