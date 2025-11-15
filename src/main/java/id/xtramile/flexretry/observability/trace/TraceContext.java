package id.xtramile.flexretry.observability.trace;

import id.xtramile.flexretry.RetryContext;

public interface TraceContext {
    void enter(RetryContext<?> ctx);
    void exit(RetryContext<?> ctx);
}
