package id.xtramile.flexretry.control.tuning;

import java.util.Objects;
import java.util.function.Supplier;

public final class ReloadableSupplier<T> implements Supplier<T> {
    private volatile T value;
    private final Supplier<T> loader;

    public ReloadableSupplier(Supplier<T> loader) {
        this.loader = Objects.requireNonNull(loader);
        this.value = loader.get();
    }

    public void reload() {
        this.value = loader.get();
    }

    @Override
    public T get() {
        return value;
    }
}
