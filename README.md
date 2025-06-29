# BitTorrent Project in Java

This project implements a simplified version of the BitTorrent protocol using Java, focusing on learning P2P (peer-to-peer) networking concepts, UDP/TCP communication, and file sharing in pieces.

## 🔑 Key Features

- ✅ Peer registration and updates with a central tracker via UDP  
- 📦 File sharing divided into pieces among peers  
- 🧠 “Rarest first” piece selection to optimize file distribution  
- 🎲 Optimistic peer selection to encourage diversity in file exchange  
- 🔗 TCP connections between peers for piece transfer  
- 🆕 Support for peers joining without any files  
- 🔁 Periodic updates of each peer’s available files  

## 🧱 Structure

- **`Peer`**: Main class representing a peer, managing connections, local files, and scheduled tasks  
- **`Tracker`**: Central server maintaining the list of active peers and their files  
- **`PeerInfo`**: Helper class holding information about each peer  

## 🛠️ Technologies Used

- Java SE (UDP and TCP sockets)  
- `ScheduledExecutorService` for periodic tasks  
- Basic client-server architecture