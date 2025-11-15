package id.xtramile.flexretry.control.batch;

import java.util.List;
import java.util.function.Function;

public interface BatchRetryStrategy<I> {
    static <I> BatchRetryStrategy<I> of(Function<List<?>, I> fn, Function<I, I> identity) {
        return ((originalInput, failures) -> fn.apply(failures));
    }

    I reduce(I originalInput, List<?> failures);
}
