import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Tracker {
    private final int port;
    private DatagramSocket datagramSocket;
    private final Map<String, PeerInfo> peers;
    private ExecutorService executor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

    public Tracker(int port) throws SocketException {
        this.port = port;
        this.peers = new HashMap<>();
        this.datagramSocket = new DatagramSocket(port);
    }

    public void start() throws IOException {
        System.out.println("Iniciando Sistema Bittorrent...");
        System.out.println("Tracker em execução!");

        startCleaner();

        while (true) {
            byte[] data = new byte[2048];
            DatagramPacket packet = new DatagramPacket(data, data.length);
            datagramSocket.receive(packet);

            executor.submit(() -> {
                try {
                    handlePacket(packet);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
    
    private void startCleaner() {
        cleaner.scheduleAtFixedRate(() -> {
            int before = peers.size();
            peers.entrySet().removeIf(entry -> entry.getValue().isExpired());
            int after = peers.size();
            if (before != after) {
                System.out.println("[cleaner] Peers expirados removidos. Total agora: " + after);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }
    
    private void handlePacket(DatagramPacket packet) throws IOException {
        String received = new String(packet.getData(), 0, packet.getLength());
        InetSocketAddress address = new InetSocketAddress(packet.getAddress(), packet.getPort());

        if (received.startsWith("CONNECT:")) {
            handlePeerConnection(received, address, packet);
        }

        if (received.startsWith("UPDATE:")) {
            handlePeerUpdate(received, address, packet);
        }
    }

    private void handlePeerConnection(String received, InetSocketAddress peerAddress, DatagramPacket packet) throws IOException {
        String content = received.substring("CONNECT:".length());
        String[] parts = content.split(":", 3);

        String peerId = parts[0];
        int peerPort = Integer.parseInt(parts[1]);
        String files = parts[2];

        PeerInfo info = new PeerInfo(peerId, peerAddress.getAddress().toString(), peerPort, files);
        info.updateLastSeen();
        peers.put(peerId, info);

        String message = buildActivePeerInfo();
        System.out.println("Enviando lista atualizada para " + peerId);

        sendResponseToPeer(packet, message);
    }

    private void handlePeerUpdate(String received, InetSocketAddress peerAddress, DatagramPacket packet) throws IOException {
        String content = received.substring("UPDATE:".length());
        String[] parts = content.split(":", 2);
        String peerId = parts[0];
        String newFiles = parts[1];

        PeerInfo info = peers.get(peerId);
        if (info != null) {
            info.setFiles(newFiles);
            System.out.println("Arquivos atualizados em " + peerId + ": " + newFiles);

            info.updateLastSeen();
        }

        String updatedList = buildActivePeerInfo();
        byte[] data = updatedList.getBytes();
        DatagramPacket response = new DatagramPacket(data, data.length, packet.getAddress(), packet.getPort());
        datagramSocket.send(response);
    }

    private void sendResponseToPeer(DatagramPacket packet, String message) throws IOException {
        byte[] responseData = message.getBytes();
        DatagramPacket response = new DatagramPacket(responseData, responseData.length, packet.getAddress(), packet.getPort());
        datagramSocket.send(response);
    }

    private String buildActivePeerInfo() {
        StringBuilder builder = new StringBuilder();

        for (Map.Entry<String, PeerInfo> entry : peers.entrySet()) {
            PeerInfo info = entry.getValue();
            String ip = info.getIp();
            ip = ip.replace("/", "");

            builder.append(info.getId())
                .append(":")
                .append(ip)
                .append(":")
                .append(info.getPort())
                .append(":")
                .append(info.getFiles())
                .append("\n");
        }

        return builder.toString();
    }
}
