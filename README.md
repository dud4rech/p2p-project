# Projeto BitTorrent em Java

Este projeto implementa uma versão simplificada do protocolo BitTorrent utilizando Java, com foco no aprendizado de conceitos de redes **P2P (peer-to-peer)**, comunicação via **UDP/TCP** e **compartilhamento de arquivos em partes**.

## 🔑 Funcionalidades Principais

- ✅ Registro e atualização de peers com um tracker central via UDP  
- 📦 Compartilhamento de arquivos divididos em partes entre os peers  
- 🧠 Seleção de pedaços pelo algoritmo “rarest first” para otimizar a distribuição  
- 🎲 Seleção otimista de peers para incentivar diversidade nas trocas  
- 🔗 Conexões TCP entre peers para transferência de arquivos  
- 🆕 Suporte para peers iniciando sem arquivos  
- 🔁 Atualizações periódicas dos arquivos disponíveis de cada peer  

## 🧱 Estrutura do Projeto

- `Peer`: Representa um peer, gerencia conexões, arquivos locais e tarefas agendadas  
- `Tracker`: Servidor central que mantém a lista de peers ativos e seus arquivos  
- `PeerInfo`: Classe auxiliar com informações de cada peer  
- `PeerMain`: Classe com `main()` para iniciar um peer  
- `TrackerMain`: Classe com `main()` para iniciar o tracker  

## 🛠️ Tecnologias Utilizadas

- Java SE (sockets UDP e TCP)  
- `ScheduledExecutorService` para tarefas periódicas  
- Arquitetura cliente-servidor básica  

---

## ▶️ Como compilar e executar (sem Maven)

### 🔧 Todos os comandos de uma vez:

```bash
# Compilar o projeto (gera classes em /bin)
javac -d bin src/*.java

# Em um terminal separado, iniciar o tracker:
java -cp bin TrackerMain

# Em outro terminal, iniciar um peer com a pasta peer1:
# Criar a pasta peer1 dentro da pasta pieces
java -cp bin PeerMain 1

# (opcional) para iniciar mais peers:
java -cp bin PeerMain 2
java -cp bin PeerMain 3