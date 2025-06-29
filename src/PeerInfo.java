public class PeerInfo {
    private final String id;
    private final String ip;
    private final int port;
    private String files;
    private long lastSeen = System.currentTimeMillis();

    public PeerInfo(String id, String ip, int port, String files) {
        this.id = id;
        this.ip = ip;
        this.port = port;
        this.files = files;
    }

    public String getId() {
        return id;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public String getFiles() {
        return files;
    }

    public void setFiles(String files) {
        this.files = files;
    }

    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - lastSeen > 5000;
    }
}
