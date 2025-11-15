package id.xtramile.flexretry.support.rand;

import java.util.concurrent.ThreadLocalRandom;

public interface RandomSource {
    static RandomSource threadLocal() {
        return ((originInclusive, boundInclusive) -> ThreadLocalRandom.current().nextLong(originInclusive, boundInclusive));
    }

    long nextLong(long originInclusive, long boundInclusive);
}
