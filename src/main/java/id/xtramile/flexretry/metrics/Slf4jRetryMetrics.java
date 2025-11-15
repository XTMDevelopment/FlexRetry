package id.xtramile.flexretry.metrics;

/**
 * Lightweight logger-based metrics without pulling SLF4J
 * Replace with a real adapter in your app if you use SLF4J/Micrometer
 */
public final class Slf4jRetryMetrics implements RetryMetrics {
    public interface Logger {
        void debug(String message);
        void info(String message);
        void warn(String message);
        void error(String message, Throwable t);
    }

    private final Logger log;

    public Slf4jRetryMetrics(Logger log) {
        this.log = log;
    }

    @Override
    public void attemptStarted(String name, int attempt) {
        log.debug(name + " attemptsStarted#" + attempt);
    }

    @Override
    public void attemptSucceeded(String name, int attempt) {
        log.info(name + " attemptsSucceeded#" + attempt);
    }

    @Override
    public void attemptFailed(String name, int attempt, Throwable error) {
        log.warn(name + " attemptsFailed#" + attempt + " err=" + (error == null ? "null" : error.getClass().getSimpleName()));
    }

    @Override
    public void exhausted(String name, int attempts, Throwable lastError) {
        log.error(name + " exhausted after " + attempts + " attempts", lastError);
    }
}
