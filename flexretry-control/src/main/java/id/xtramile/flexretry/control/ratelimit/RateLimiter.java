package id.xtramile.flexretry.control.ratelimit;

public interface RateLimiter {
    boolean tryAcquire();
}
