package id.xtramile.flexretry.integrations.http;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Example extractor that looks for headers on a custom HttpResponse/HttpException
 * Adjust the accessors to your HTTP client types;
 */
public final class HttpRetryAfterExtractor<T> implements RetryAfterExtractor<T> {

    @Override
    public Duration extract(Throwable error, T result) {
        Duration duration = tryParseHeader(result);

        if (duration != null) {
            return duration;
        }

        return tryParseHeader(error);
    }

    private Duration tryParseHeader(Object carrier) {
        if (carrier == null) {
            return null;
        }

        try {
            String retryAfter = (String) carrier.getClass().getMethod("getHeader", String.class)
                    .invoke(carrier, "Retry-After");

            if (retryAfter == null) {
                return null;
            }

            retryAfter = retryAfter.trim();

            if (retryAfter.matches("\\d+")) {
                return Duration.ofSeconds(Long.parseLong(retryAfter));
            }

            ZonedDateTime when = ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ROOT));
            long secs = Math.max(0, Duration.between(ZonedDateTime.now(when.getZone()), when).getSeconds());

            return Duration.ofSeconds(secs);

        } catch (Throwable ignore) {
            return null;
        }
    }
}
