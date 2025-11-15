package id.xtramile.flexretry.http;

import java.time.Duration;

public interface RetryAfterExtractor<T> {
    Duration extract(Throwable error, T result);
}
