import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Peer {
    private final String id;
    private final int port;
    private final String ip;
    private final static String trackerIp = "localhost";
    private final int trackerPort = 8888;

    private Map<String, PeerInfo> activePeers;
    private String lastRarestPeerId = null;

    private final DatagramSocket datagramSocket;
    private final ScheduledExecutorService scheduler;
    private ServerSocket serverSocket;

    public Peer(int id, String ip) throws IOException {
        this.id = "peer" + id;
        this.ip = ip;
        this.port = 5000 + id;
        this.datagramSocket = new DatagramSocket();
        this.scheduler = Executors.newScheduledThreadPool(4);
    }

    public void start() {
        try {
            this.serverSocket = new ServerSocket(this.port);
            System.out.println("Iniciando peer...");
            System.out.println("Conectando-se ao tracker...");
            connectToTracker();

            System.out.println("Aceitando conexões...");
            acceptPeerConnections();

            System.out.println("Iniciando tarefas periódicas...");
            startPeriodicTasks();
        } catch (Exception e) {
            System.out.println("Erro ao inicializar peer: " + e);
        }
    }

    private void startPeriodicTasks() {
        scheduler.scheduleAtFixedRate(this::updateFileList, 5, 3, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::requestRarestFile, 10, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::optimisticPeerSelection, 12, 12, TimeUnit.SECONDS);
    }

    private void connectToTracker() {
        try {
            String message = "CONNECT:" + id + ":" + port + ":" + listFiles();
            byte[] data = message.getBytes();
            DatagramPacket request = new DatagramPacket(
                    data, data.length, InetAddress.getByName(trackerIp), trackerPort);
            datagramSocket.send(request);

            byte[] buffer = new byte[2048];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            datagramSocket.receive(response);
            String responseText = new String(response.getData(), 0, response.getLength());

            System.out.println("Lista com pares ativos no torrent: \n" + responseText);

            this.activePeers = parseActivePeers(responseText);
        } catch (IOException e) {
            System.out.println("Erro ao conectar com tracker: " + e);
        }
    }

    private void updateFileList() {
        try {
            String message = "UPDATE:" + id + ":" + listFiles();
            byte[] data = message.getBytes();
            DatagramPacket request = new DatagramPacket(
                    data, data.length, InetAddress.getByName(trackerIp), trackerPort);
            datagramSocket.send(request);

            byte[] buffer = new byte[2048];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            datagramSocket.receive(response);
            String responseText = new String(response.getData(), 0, response.getLength());

            System.out.println("[" + id + "] Lista de pedaços enviada ao tracker.");

            this.activePeers = parseActivePeers(responseText);
        } catch (IOException e) {
            System.out.println("Erro ao enviar lista de pedaços ao tracker: " + e);
        }
    }

    private void requestRarestFile() {
        if (activePeers == null || activePeers.isEmpty()) {
            System.out.println("[rarest first] Nenhum peer ativo para solicitar arquivos.");
            return;
        }

        String rarestFile = findRarestFile();
        if (rarestFile == null || rarestFile.isEmpty()) {
            System.out.println("[rarest first] Nenhum arquivo raro encontrado.");
            return;
        }

        File localFile = new File("src/pieces/" + id + "/" + rarestFile);
        if (localFile.exists()) {
            return;
        }

        PeerInfo fileOwner = findPeerWithFile(rarestFile);
        if (fileOwner == null || fileOwner.getId().equals(this.id)) {
            System.out.println("[rarest first] Nenhum outro peer com o arquivo mais raro. Aguardando...");
            return;
        }

        this.lastRarestPeerId = fileOwner.getId();
        System.out.println("[rarest first] Solicitando o arquivo " + rarestFile + " ao " + fileOwner.getId());

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(fileOwner.getIp(), fileOwner.getPort()), 5000);
            socket.setSoTimeout(10000);

            try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                 InputStream input = socket.getInputStream()) {

                out.write("REQUEST_RAREST:" + rarestFile + "\n");
                out.flush();

                File outputFile = new File("src/pieces/" + id + "/" + rarestFile);
                outputFile.getParentFile().mkdirs();

                try (FileOutputStream fileOut = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = input.read(buffer)) != -1) {
                        fileOut.write(buffer, 0, bytesRead);
                    }
                }
                System.out.println("[rarest first] Arquivo " + rarestFile + " recebido com sucesso!");
            }
        } catch (ConnectException e) {
            System.out.println("[rarest first] Erro ao conectar com " + fileOwner.getId() + ": " + e.getMessage());
        } catch (IOException e) {
            System.out.println("[rarest first] Erro ao conectar com " + fileOwner.getId() + ": " + e.getMessage());
        }
    }

    private void optimisticPeerSelection() {
        if (activePeers == null || activePeers.isEmpty()) {
            System.out.println("[optimistic] Nenhum peer ativo para seleção aleatória.");
            return;
        }

        List<PeerInfo> availablePeers = activePeers.values().stream().toList();
        Random random = new Random();
        PeerInfo optimisticPeer = availablePeers.get(random.nextInt(availablePeers.size()));
        System.out.println("[optimistic] Selecionando peer aleatório: " + optimisticPeer.getId());

        String[] files = optimisticPeer.getFiles().split(";");

        for (String fileName : files) {
            if (fileName.isBlank()) continue;

            File localFile = new File("src/pieces/" + id + "/" + fileName);
            if (localFile.exists()) continue;

            try (Socket socket = new Socket(optimisticPeer.getIp(), optimisticPeer.getPort());
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                InputStream input = socket.getInputStream()) {

                out.write("REQUEST_FILE:" + fileName + "\n");
                out.flush();

                File outputFile = new File("src/pieces/" + id + "/" + fileName);
                outputFile.getParentFile().mkdirs();

                try (FileOutputStream fileOut = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = input.read(buffer)) != -1) {
                        fileOut.write(buffer, 0, bytesRead);
                    }
                }

                System.out.println("[optimistic] Arquivo baixado de " + optimisticPeer.getId() + ": " + fileName);
            } catch (ConnectException e) {
                System.out.println("[optimistic] Erro ao conectar com " + optimisticPeer.getId() + ": " + e.getMessage());
            } catch (IOException e) {
                System.out.println("[optimistic] Erro ao baixar " + fileName + " de " + optimisticPeer.getId() + ": " + e.getMessage());
            }

            break;
        }

        lastRarestPeerId = null;
    }

    private String listFiles() {
        File dir = new File("src/pieces/" + id);
        File[] filesArr = dir.listFiles();

        StringBuilder filesList = new StringBuilder();
        if (filesArr != null) {
            for (File file : filesArr) {
                if (file.isFile()) {
                    filesList.append(file.getName()).append(";");
                }
            }
        }
        return filesList.toString();
    }

    private Map<String, PeerInfo> parseActivePeers(String response) {
        Map<String, PeerInfo> activePeers = new HashMap<>();

        String[] lines = response.split("\n");
        for (String line : lines) {
            String[] parts = line.split(":");
            if (parts.length >= 4) {
                String id = parts[0];
                String ip = parts[1];
                int port = Integer.parseInt(parts[2]);
                String files = parts[3];

                if (!id.equals(this.id)) {
                    activePeers.put(id, new PeerInfo(id, ip, port, files));
                }
            }
        }

        return activePeers;
    }

    private String findRarestFile() {
        Map<String, Integer> fileFrequency = new HashMap<>();

        for (PeerInfo peer : activePeers.values()) {
            if (peer.getId().equals(this.id)) continue;

            String[] files = peer.getFiles().split(";");
            for (String file : files) {
                file = file.trim();
                if (hasLocalFile(file)) continue;

                fileFrequency.put(file, fileFrequency.getOrDefault(file, 0) + 1);
            }
        }

        if (fileFrequency.isEmpty()) {
            return null;
        }

        return fileFrequency.entrySet()
                .stream()
                .min(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private boolean hasLocalFile(String filename) {
        File localFile = new File("src/pieces/" + id + "/" + filename);
        return localFile.exists();
    }

    private PeerInfo findPeerWithFile(String file) {
        for (PeerInfo peer : activePeers.values()) {
            if (Arrays.asList(peer.getFiles().split(";")).contains(file)) {
                return peer;
            }
        }
        return null;
    }

    private void handlePeerRequest(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             OutputStream outputStream = socket.getOutputStream()) {

            String request = in.readLine();

            if (request.startsWith("REQUEST_RAREST")) {
               String fileName = request.split(":")[1];
               File file = new File("src/pieces/" + id + "/" + fileName);

               if (file.exists()) {
                   byte[] buffer = new byte[4096];
                   try (FileInputStream fileInput = new FileInputStream(file)) {
                       int bytesRead;
                       while ((bytesRead = fileInput.read(buffer)) != -1) {
                           outputStream.write(buffer, 0, bytesRead);
                       }
                       outputStream.flush();
                   }
               } else {
                   out.write("Arquivo não encontrado.");
                   out.flush();
               }
            }
        } catch (IOException e) {
           throw new RuntimeException(e);
        }
    }

    private void acceptPeerConnections() {
        scheduler.submit(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    handlePeerRequest(socket);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
