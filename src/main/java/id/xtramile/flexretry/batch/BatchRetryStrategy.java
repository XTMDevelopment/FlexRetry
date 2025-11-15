package id.xtramile.flexretry.batch;

import java.util.List;
import java.util.function.Function;

public interface BatchRetryStrategy<I> {
    I reduce(I originalInput, List<?> failures);

    static <I> BatchRetryStrategy<I> of(Function<List<?>, I> fn, Function<I, I> identity) {
        return ((originalInput, failures) -> fn.apply(failures));
    }
}
