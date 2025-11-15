package id.xtramile.flexretry.support.time;

public interface Clock {
    static Clock system() {
        return System::nanoTime;
    }

    long nanoTime();
}
