package id.xtramile.flexretry.support.time;

public interface Clock {
    long nanoTime();

    static Clock system() {
        return System::nanoTime;
    }
}
