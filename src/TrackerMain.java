import java.io.IOException;

public class TrackerMain {
    private static Tracker tracker;
    private static final int port = 8888;

    public static void main(String[] args) throws IOException {
        tracker = new Tracker(port);
        tracker.start();
    }
}
