package ru.nsu.fit.g15205.shishlyannikov;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.*;

public class MyServerSocket {
    enum State { LISTEN, SYN_RECEIVED }
    private final byte SYN_FLAG = 1;
    private final byte ACK_FLAG = 2;
    private final byte FIN_FLAG = 4;

    private final short HEADER_SIZE = 9;
    private final short MAX_PACKET_SIZE = 1024;

    private final int timeout = 1000;

    private int sequenceNum = 1;
    private DatagramSocket datagramSocket;
    private State state = State.LISTEN;

    private ConcurrentHashMap<String, MySocket> sockets = new ConcurrentHashMap<>(); // (адрес + порт) : сокет
    private BlockingQueue<DatagramPacket> forAccept = new LinkedBlockingDeque<>();   // пакеты для accept

    private Thread receiver;
    private Thread sender;

    public MyServerSocket(int port) throws SocketException {
        datagramSocket = new DatagramSocket(port);
        datagramSocket.setSoTimeout(timeout);

        receiver = new Thread(() -> {
            ByteBuffer data;
            InetAddress address;
            int clientPort;
            String clientName;

            while (!Thread.interrupted()) {
                try {
                    byte[] buffer = new byte[MAX_PACKET_SIZE];
                    DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);

                    datagramSocket.receive(receivedPacket);

                    address = receivedPacket.getAddress();
                    clientPort = receivedPacket.getPort();

                    clientName = address + " " + clientPort;

                    data = ByteBuffer.wrap(receivedPacket.getData());
                    int seq = data.getInt();
                    int ackNum = data.getInt();
                    byte flags = data.get();

                    // если с клиентом уже работаем
                    if (sockets.containsKey(clientName)) {
                        // на акк удаляем у сокета соответствующий пакет
                        if ((flags & ACK_FLAG) == ACK_FLAG) {
                            sockets.get(clientName).removePacket(ackNum);
                            if (seq == sockets.get(clientName).getExpectedSeqNum()) {
                                sockets.get(clientName).incExpectedSeqNum();
                            }
                            continue;
                        }

                        // если то, что ждали, ок, иначе, если уже было, еще раз отправляем акк, иначе запоминаем
                        if (seq != sockets.get(clientName).getExpectedSeqNum()) {
                            if (seq < sockets.get(clientName).getExpectedSeqNum()) {
                                sockets.get(clientName).sendAck(++seq);
                            }
                            else {
                                sockets.get(clientName).addUnexpectedPacket(seq, receivedPacket);
                                sockets.get(clientName).sendAck(++seq);
                            }
                            continue;
                        }

                        sockets.get(clientName).incExpectedSeqNum();

                        sockets.get(clientName).sendAck(++seq); //на все остальное отправляем акк

                        if ((flags & FIN_FLAG) == FIN_FLAG) {
                            sockets.get(clientName).sendFinAck(++seq);
                        }

                        sockets.get(clientName).addPacket(receivedPacket); // пихаем в очередь сокету

                        continue;
                    }

                    if (((flags & SYN_FLAG) == SYN_FLAG) || ((flags & ACK_FLAG) == ACK_FLAG)) {
                        forAccept.add(receivedPacket); // если новый клиент, и это syn или ack отдаем accept(у)
                    }
                } catch (SocketTimeoutException ex) {
                    //System.out.println("timeout");
                    continue;
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        sender = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    for (Map.Entry<String, MySocket> entry : sockets.entrySet()) {
                        // удаляем закрытые сокеты
                        if (entry.getValue().isClosed()) {
                            //System.out.println("Connection remove");
                            sockets.remove(entry.getKey());
                            continue;
                        }

                        // отправляем все акки
                        ArrayList<DatagramPacket> acks = entry.getValue().getAcksForSend();

                        for (DatagramPacket packet : acks) {
                            datagramSocket.send(packet);
                        }

                        // отправляем все пакеты
                        ArrayList<DatagramPacket> forSend = entry.getValue().getPacketsForSend();

                        for (DatagramPacket packet : forSend) {
                            datagramSocket.send(packet);
                        }
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

        receiver.start();
        sender.start();
    }

    public MySocket accept() {
        int clientPort;
        int clientSeq;
        int ackNum;
        byte flags;
        InetAddress address;

        MySocket mySocket;

        while (true) {
            try {
                DatagramPacket receivedPacket = forAccept.take();

                ByteBuffer data = ByteBuffer.wrap(receivedPacket.getData());

                address = receivedPacket.getAddress();
                clientPort = receivedPacket.getPort();

                // парсим заголовок
                clientSeq = data.getInt();
                ackNum = data.getInt();
                flags = data.get();

                if ((state == State.LISTEN) && ((flags & SYN_FLAG) == SYN_FLAG)) {
                    // новое соединение, получили syn
                    data.clear();

                    data.putInt(sequenceNum++);
                    data.putInt(++clientSeq);
                    data.put((byte)(SYN_FLAG | ACK_FLAG));

                    DatagramPacket packet = new DatagramPacket(data.array(), data.capacity(), address, clientPort);

                    try {
                        // отправляем syn ack пакет и переходим в состояние ожидания ответа
                        datagramSocket.send(packet);
                        state = State.SYN_RECEIVED;
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                } else if ((state == State.SYN_RECEIVED) && ((flags & ACK_FLAG) == ACK_FLAG) && (ackNum == sequenceNum)) {
                    mySocket = new MySocket(receivedPacket.getAddress(), receivedPacket.getPort(), ++clientSeq);
                    sockets.put(receivedPacket.getAddress() + " " + receivedPacket.getPort(), mySocket);

                    // получили ответ на наш syn ack, соединение установлено
                    state = State.LISTEN;
                    System.out.println("Connection Established with " + receivedPacket.getAddress() + " " + receivedPacket.getPort());
                    return mySocket;
                }
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    public boolean close() {
        boolean res = true;

        // закрываем все наши сокеты, если хоть один завершился неуспешно, то и серверсокет звершается неуспешно
        for (MySocket socket : sockets.values()) {
            try {
                if (!socket.close()) {
                    res = false;
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        //убиваем ресивера, если он висит на receive, то с первого раза не получится
        while (!receiver.isInterrupted()) {
            receiver.interrupt();
        }
        sender.interrupt();

        return res;
    }
}
