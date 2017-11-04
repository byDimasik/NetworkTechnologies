package ru.nsu.fit.g15205.shishlyannikov;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.concurrent.*;

public class MyServerSocket {
    enum State { LISTEN, SYN_RECEIVED, ESTABLISHED }
    private final byte SYN_FLAG = 1;
    private final byte ACK_FLAG = 2;
    private final byte FIN_FLAG = 4;
    private final byte RST_FLAG = 8;

    private final short HEADER_SIZE = 9;
    private final short MAX_PACKET_SIZE = HEADER_SIZE + 1024;
    private final int timeout = 10000;

    private int sequenceNum = 1;
    private DatagramSocket datagramSocket;
    private State state = State.LISTEN;

    private ConcurrentHashMap<String, MySocket> sockets = new ConcurrentHashMap<>();
    private BlockingQueue<DatagramPacket> forSend = new LinkedBlockingDeque<>();
    private BlockingQueue<DatagramPacket> forAccept = new LinkedBlockingDeque<>();

    public MyServerSocket(int port) throws SocketException {
        datagramSocket = new DatagramSocket(port);
        datagramSocket.setSoTimeout(timeout);

        Thread receiver = new Thread(() -> {
            byte[] buffer = new byte[MAX_PACKET_SIZE];
            DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
            ByteBuffer data;
            InetAddress address;
            int clientPort;
            String clientName;

            while (!Thread.interrupted()) { // TODO сделать чтобы ресивер удалял у клиентов пакеты, на которые пришли аки
                try {
                    datagramSocket.receive(receivedPacket);

                    address = receivedPacket.getAddress();
                    clientPort = receivedPacket.getPort();

                    clientName = address + " " + clientPort;

                    if (sockets.containsKey(clientName)) {
                        sockets.get(clientName).addPacket(receivedPacket);
                        continue;
                    }

                    data = ByteBuffer.wrap(receivedPacket.getData());
                    data.getInt();
                    data.getInt();
                    byte flags = data.get();

                    if (((flags & SYN_FLAG) == SYN_FLAG) || ((flags & ACK_FLAG) == ACK_FLAG)) {
                        forAccept.add(receivedPacket);
                    }
                } catch (SocketTimeoutException ex) {
                    System.out.println("timeout");
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        Thread sender = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    DatagramPacket packet = forSend.take();

                    datagramSocket.send(packet);
                } catch (InterruptedException | IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

        receiver.start();
        sender.start();
    }

    // TODO добавить создание сокета
    // TODO добавить отправку RST если все плохо
    // TODO переделать на forAccept
    public MySocket accept() {
        long startTime = 0;
        int clientPort;
        int clientSeq;
        int ackNum;
        byte flags;
        InetAddress address;

        byte[] buffer = new byte[HEADER_SIZE];

        while (true) {
            if ((state == State.SYN_RECEIVED) && ((System.currentTimeMillis() - startTime) > timeout)) {
                state = State.LISTEN;
                continue;
            }

            DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);

            try {
                datagramSocket.receive(receivedPacket);
            } catch (SocketTimeoutException ex) {
                // на случай если таймаут был в состоянии SYN_RECEIVED
                state = State.LISTEN;
                continue;
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            ByteBuffer data = ByteBuffer.wrap(receivedPacket.getData());

            address = receivedPacket.getAddress();
            clientPort = receivedPacket.getPort();

            // парсим заголовок
            clientSeq = data.getInt();
            ackNum = data.getInt();
            flags = data.get();

            System.out.println(address + " " + clientPort + " " + clientSeq + " " + flags);
            if ((state == State.LISTEN) && ((flags & SYN_FLAG) == SYN_FLAG)) {
                // новое соединение
                data.clear();

                data.putInt(sequenceNum++);
                data.putInt(++clientSeq);
                data.put((byte)(SYN_FLAG | ACK_FLAG));

                DatagramPacket packet = new DatagramPacket(data.array(), data.capacity(), address, clientPort);

                try {
                    // отправляем syn ack пакет и переходим в состояние ожидания ответа
                    datagramSocket.send(packet);
                    startTime = System.currentTimeMillis();
                    state = State.SYN_RECEIVED;
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            } else if ((state == State.SYN_RECEIVED) && ((flags & ACK_FLAG) == ACK_FLAG) && (ackNum == sequenceNum)) {
                // получили ответ на наш syn ack, соединение установлено
                state = State.ESTABLISHED;
                System.out.println("Connection Established");
                return null;
            }
        }
    }

    synchronized public void addToSend(DatagramPacket packet) {
        forSend.add(packet);
    }
}
