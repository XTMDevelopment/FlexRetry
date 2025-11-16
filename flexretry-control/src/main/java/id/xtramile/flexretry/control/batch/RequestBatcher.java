package id.xtramile.flexretry.control.batch;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class RequestBatcher<K, R> {
    private final int maxBatchSize;
    private final long flushEveryMillis;
    private final Function<List<K>, List<R>> transport;
    private final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
    private final List<K> buffer = new ArrayList<>();

    public RequestBatcher(int maxBatchSize, Duration flushEvery, Function<List<K>, List<R>> transport) {
        this.maxBatchSize = maxBatchSize;
        this.flushEveryMillis = flushEvery.toMillis();
        this.transport = transport;

        ses.scheduleAtFixedRate(this::flush, flushEveryMillis, flushEveryMillis, TimeUnit.MILLISECONDS);
    }

    public synchronized List<R> addAndMaybeFlush(K key) {
        buffer.add(key);

        if (buffer.size() >= maxBatchSize) {
            return flush();
        }

        return List.of();
    }

    public synchronized List<R> flush() {
        if (buffer.isEmpty()) {
            return List.of();
        }

        List<K> batch = new ArrayList<>(buffer);
        buffer.clear();

        return transport.apply(batch);
    }
}
