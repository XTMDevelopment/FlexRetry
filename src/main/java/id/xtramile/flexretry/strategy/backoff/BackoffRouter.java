package id.xtramile.flexretry.strategy.backoff;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class BackoffRouter {
    public static final class Route {
        final Predicate<Throwable> predicate;
        final BackoffStrategy backoff;

        Route(Predicate<Throwable> predicate, BackoffStrategy backoff) {
            this.predicate = predicate;
            this.backoff = backoff;
        }
    }

    private final List<Route> routes = new ArrayList<>();
    private BackoffStrategy defaultBackoff = attempt -> Duration.ZERO;

    public BackoffRouter when(Predicate<Throwable> predicate, BackoffStrategy backoff) {
        routes.add(new Route(Objects.requireNonNull(predicate), Objects.requireNonNull(backoff)));
        return this;
    }

    public BackoffRouter defaultTo(BackoffStrategy backoff) {
        this.defaultBackoff = Objects.requireNonNull(backoff);
        return this;
    }

    public BackoffStrategy select(Throwable error) {
        if (error != null) {
            for (Route route : routes) {
                if (route.predicate.test(error)) {
                    return route.backoff;
                }
            }
        }

        return defaultBackoff;
    }
}
