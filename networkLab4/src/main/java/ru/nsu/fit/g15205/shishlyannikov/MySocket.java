package ru.nsu.fit.g15205.shishlyannikov;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

public class MySocket {
    enum State { CLOSED, ESTABLISHED, FIN }
    private final byte SYN_FLAG = 1;
    private final byte ACK_FLAG = 2;
    private final byte FIN_FLAG = 4;
    private final byte RST_FLAG = 8;

    private final short HEADER_SIZE = 9;
    private final short MAX_PACKET_SIZE = HEADER_SIZE + 1024;

    private DatagramSocket datagramSocket = null;
    private BlockingQueue<DatagramPacket> inPackets = new LinkedBlockingDeque<>(); // входящие пакеты
    //мапа с исходящими пакетами, ключ - номер ака, который должен прийти на этот пакет
    private ConcurrentHashMap<Integer, DatagramPacket> outPackets = new ConcurrentHashMap<>();
    //аки, которые нужно отправить
    private BlockingQueue<DatagramPacket> acks = new LinkedBlockingDeque<>();

    private int sequenceNum = 1; // наш sequence number
    private int expectedSeqNum;  // ожидаемый sequence number

    private int destinationPort;
    private InetAddress destinationAddress;

    private State state = State.CLOSED;

    private Thread sender;
    private Thread receiver;

    private final Object obj = new Object();

    /***
     * Конструктор для MySocketServer, в этом конструкторе не создается своего udp сокета
     * @param host - адрес
     * @param port - порт
     * @param clientSeq - ожидаемый sequence number
     */
    public MySocket(InetAddress host, int port, int clientSeq) {
        destinationAddress = host;

        destinationPort = port;
        expectedSeqNum = clientSeq;
        state = State.ESTABLISHED;
    }

    /***
     * Обычный конструктор для клиентов, тут у сокета есть свой udp сокет
     * @param host - адрес
     * @param port - порт
     */
    public MySocket(String host, int port) throws SocketException {
        datagramSocket = new DatagramSocket();
        datagramSocket.setSoTimeout(10000);

        this.destinationPort = port;

        try {
            this.destinationAddress = InetAddress.getByName(host);
        } catch (UnknownHostException ex) {
            ex.printStackTrace();
        }

        // тк серверсокета у нас в этом случае нет, этот поток эмулирует его работу: принимает пакеты и запихивает
        // их в очередь для приема
        receiver = new Thread(() -> {
            synchronized (obj) {
                try {
                    obj.wait(); // ждем коннекта
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }

            // работаем, пока сокет не закроют, а потом пока не отправим все пакеты и не дождемся на них акков
            while (state != State.FIN || !outPackets.isEmpty()) {
                try {
                    byte[] buffer = new byte[MAX_PACKET_SIZE];
                    DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);

                    datagramSocket.receive(receivedPacket);

                    ByteBuffer data = ByteBuffer.wrap(receivedPacket.getData());
                    int seq = data.getInt();
                    int ackNum = data.getInt();
                    byte flags = data.get();

                    // если получили не тот пакет, который ждем, пропускаем его без акка (клиент потом отправит снова)
                    // если пакет уже получали, отправляем на него акк еще раз
                    if (seq != expectedSeqNum) {
                        if (seq < expectedSeqNum) {
                            sendAck(++seq);
                        }
                        continue;
                    }

                    // ждем следующий пакет
                    expectedSeqNum++;

                    // если получили акк, удаляем пакет, который ждал этого акка
                    if ((flags & ACK_FLAG) == ACK_FLAG) {
                        removePacket(ackNum);
                        continue;
                    }

                    // в любом другом случае отправляем акк на пришедший пакет
                    sendAck(++seq);

                    // если пришел флаг разорвать соединение, переходим в состояние завершения
                    if ((flags & RST_FLAG) == RST_FLAG) {
                        state = State.FIN;
                        continue;
                    }

                    // если надо завершить работу, не принимаем больше пакетов, иначе помещаем пакет в очередь
                    // для последующей обработки
                    if (state != State.FIN) {
                        inPackets.add(receivedPacket);
                    }
                } catch (SocketTimeoutException ex) {
                    //ystem.out.println("timeout");
                    continue;
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        receiver.start();

        // а этот поток отправляет из очереди для отправки
        sender = new Thread(() -> {
            // работаем, пока не нужно завершаться и пока есть, на что получить акки
            while (state != State.FIN || !outPackets.isEmpty()) {
                try {
                    ArrayList<DatagramPacket> acksForSend = getAcksForSend();

                    for (DatagramPacket packet : acksForSend) {
                        datagramSocket.send(packet);
                    }

                    ArrayList<DatagramPacket> forSend = getPacketsForSend();

                    for (DatagramPacket packet : forSend) {
                        datagramSocket.send(packet);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        sender.start();

        // коннектимся к серверу по переданному адресу
        try {
            connect();
        } catch (IOException ex) {
            System.err.println(ex.getLocalizedMessage());
        }
    }

    /***
     * Классический коннект с тройным рукопожатием
     */
    private void connect() throws IOException {
        while (true) {
            ByteBuffer data = ByteBuffer.allocate(HEADER_SIZE);

            data.putInt(sequenceNum++);
            data.putInt(0);
            data.put(SYN_FLAG);
            data.flip();

            DatagramPacket packet = new DatagramPacket(data.array(), data.capacity(), destinationAddress, destinationPort);

            try {
                datagramSocket.send(packet); // отправляем SYN

                data.clear();

                byte[] buffer = new byte[HEADER_SIZE];

                DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
                datagramSocket.receive(receivedPacket);
                data = ByteBuffer.wrap(receivedPacket.getData());
            } catch (SocketTimeoutException ex) {
                continue;
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            int serverSeq = data.getInt();
            int ack = data.getInt();
            byte flags = data.get();

            if ((flags & SYN_FLAG) == SYN_FLAG) {
                if (((flags & ACK_FLAG) == ACK_FLAG) && (ack == sequenceNum)) {
                    // получаем в ответ SYN ACK и отправляем свой ACK
                    sendAck(++serverSeq);
                    expectedSeqNum = serverSeq;

                    state = State.ESTABLISHED;
                    synchronized (obj) {
                        obj.notify(); // будим ресивера
                    }
                    System.out.println("Connection Established");
                    break;
                } else {
                    throw new IOException("Received SYN, but not ACK. Or ack num wrong");
                }
            } else if ((flags & RST_FLAG) == RST_FLAG) {
                throw new IOException("RST received");
            }

            try {
                Thread.sleep(10000); // через 10 секунд пробуем коннектиться снова
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    /***
     * Отправка акка
     * @param ackNum - номер акка
     */
    public void sendAck(int ackNum) {
        ByteBuffer data = ByteBuffer.allocate(20);

        data.putInt(sequenceNum++);
        data.putInt(ackNum);
        data.put(ACK_FLAG);
        data.flip();

        DatagramPacket packet = new DatagramPacket(data.array(), data.capacity(), destinationAddress, destinationPort);

        acks.add(packet);
    }

    public int getExpectedSeqNum() {
        return expectedSeqNum;
    }

    public void incExpectedSeqNum() {
        expectedSeqNum++;
    }

    /***
     * Добавить входящий пакет
     * @param packet - пакет
     */
    public void addPacket(DatagramPacket packet) {
        inPackets.add(packet);
    }

    /***
     * Принимаем данные
     * @param buffer и кладем их сюда
     * @return возвращаем количество считанных байт
     */
    public int receive(byte[] buffer) {
        // если мы в состоянии завершения, ничего не принимаем
        if (state == State.FIN) {
            return 0;
        }

        while (true) {
            try {
                DatagramPacket packet = inPackets.take(); // забираем пакет из очереди

                ByteBuffer data = ByteBuffer.wrap(packet.getData());

                data.getInt();
                data.getInt();
                data.get();

                int size = data.getInt();

                data.get(buffer);
                return size;
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    /***
     * Отправляем
      * @param buffer - массив байт для отправки
     */
    public void send(byte[] buffer) throws SocketException {
        if (state == State.FIN) {
            throw new SocketException("socket closed");
        }

        int size;

        // если пакет больше, чем MAX_PACKET_SIZE бьем его на куски равные MAX_PACKET_SIZE
        for (int i = 0; i < buffer.length; i += (MAX_PACKET_SIZE - HEADER_SIZE)) {
            size = (buffer.length - i) > (MAX_PACKET_SIZE - HEADER_SIZE) ? MAX_PACKET_SIZE : ((buffer.length - i) + HEADER_SIZE);

            ByteBuffer data = ByteBuffer.allocate(size + 4);

            data.putInt(sequenceNum++);
            data.putInt(0);
            data.put((byte) 0);

            data.putInt(size - HEADER_SIZE);
            data.put(buffer);

            DatagramPacket packet = new DatagramPacket(data.array(), data.capacity(), destinationAddress, destinationPort);

            outPackets.put(sequenceNum, packet);
        }
    }

    public ArrayList<DatagramPacket> getPacketsForSend() {
        return new ArrayList<>(outPackets.values());
    }

    public ArrayList<DatagramPacket> getAcksForSend() {
        ArrayList<DatagramPacket> ret = new ArrayList<>();

        acks.drainTo(ret); // забираем все из очереди и кладем в лист

        return ret;
    }

    /***
     * Удалить пакет по номеру ожидаемого акка
     * @param ackNum - номер пришедшего акка
     */
    public void removePacket(int ackNum) {
        if (outPackets.containsKey(ackNum)) {
            outPackets.remove(ackNum);
        }
        // если ждем завершения работы и это был последний пакет, ожидающий акка, будим close()
        if (state == State.FIN && outPackets.isEmpty()) {
            synchronized (obj) {
                obj.notify();
            }
        }
    }

    /***
     * Закрыть наш сокет
     * @return - true при успешном закрытии (отправили все, что хотели и получили на это акки), false - иначе
     */
    public boolean close() throws IOException {
        // если уже закрыты
        if (state == State.FIN) {
            return true;
        }
        state = State.FIN; // переходим в состояние завершения

        // если закрывают сокет порожденный серверсокетом
        if (datagramSocket == null) {
            ByteBuffer data = ByteBuffer.allocate(HEADER_SIZE);

            data.putInt(sequenceNum++);
            data.putInt(0);
            data.put(RST_FLAG); // отправляем клиенту разрыв соединения

            DatagramPacket packet = new DatagramPacket(data.array(), data.capacity(), destinationAddress, destinationPort);

            outPackets.put(sequenceNum, packet);

            synchronized (obj) {
                try {
                    obj.wait(10000); // даем 10 секунд за завершение всех дел, либо пока нас не разбудят
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }

            if (!isClosed()) { // если что то не закончили, очищаем очередь сообщений для отправки, возвращаем false
                outPackets.clear();
                return false;
            }

            System.out.println("Socket closed");
            return true; // иначе все хорошо
        }

        // если закрывают клиентский сокет, ждем пока закончат работу receiver и sender (то есть пока получим все акки)
        try {
            receiver.join();
            sender.join();
        } catch (InterruptedException ex) {
            throw new IOException("interrupted");
        }

        ByteBuffer data = ByteBuffer.allocate(HEADER_SIZE);

        data.putInt(sequenceNum++);
        data.putInt(0);
        data.put(FIN_FLAG); // отправляем fin

        DatagramPacket packet = new DatagramPacket(data.array(), data.capacity(), destinationAddress, destinationPort);

        datagramSocket.send(packet);

        while (true) { // ждем акка
            byte[] buffer = new byte[HEADER_SIZE];

            DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);

            try {
                datagramSocket.receive(receivedPacket);

                data = ByteBuffer.wrap(receivedPacket.getData());

                int seqNum = data.getInt();
                int ack = data.getInt();
                byte flags = data.get();

                if (((flags & FIN_FLAG) == FIN_FLAG) && ((flags & ACK_FLAG) == ACK_FLAG) && (ack == sequenceNum)) {
                    // на фин акк отправляем свой акк и завершаем работу
                    sendAck(++seqNum);
                    return true;
                }
            } catch (SocketTimeoutException ex) {
                return false; // если акка не дождались
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void sendFinAck(int ackNum) {
        ByteBuffer data = ByteBuffer.allocate(HEADER_SIZE);

        data.putInt(sequenceNum++);
        data.putInt(ackNum);
        data.put((byte)(ACK_FLAG | FIN_FLAG));

        outPackets.put(sequenceNum, new DatagramPacket(data.array(), data.capacity(), destinationAddress, destinationPort));
        state = State.FIN;
    }

    public boolean isClosed() {
        // мы завершили работу если нам сказали это сделать, и очереди на отправку и принятие пусты
        return (state == State.FIN) && inPackets.isEmpty() && outPackets.isEmpty();
    }
}
