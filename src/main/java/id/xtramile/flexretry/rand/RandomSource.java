package id.xtramile.flexretry.rand;

import java.util.concurrent.ThreadLocalRandom;

public interface RandomSource {
    long nextLong(long originInclusive, long boundInclusive);

    static RandomSource threadLocal() {
        return ((originInclusive, boundInclusive) -> ThreadLocalRandom.current().nextLong(originInclusive, boundInclusive));
    }
}
