package id.xtramile.flexretry.time;

public interface Clock {
    long nanoTime();

    static Clock system() {
        return System::nanoTime;
    }
}
