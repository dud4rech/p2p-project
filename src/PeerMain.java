import java.io.IOException;

public class PeerMain {
    private static Peer peer;
    private static final String ip = "localhost";

    public static void main(String[] args) throws IOException {
        int num = Integer.parseInt(args[0]);
        peer = new Peer(num, ip);
        peer.start();
    }
}
