package id.xtramile.flexretry.observability.trace;

public interface TraceScope extends AutoCloseable {
    @Override
    void close() throws Exception;
}
