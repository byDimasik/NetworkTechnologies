package ru.nsu.fit.g15205.shishlyannikov;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;

public class MyServerSocket {
    enum State { LISTEN, SYN_SENT, SYN_RECEIVED, ESTABLISHED }
    private final short SYN_FLAG = 1;
    private final short ACK_FLAG = 2;
    private final short FIN_FLAG = 4;
    private final short RST_FLAG = 8;

    private final short HEADER_SIZE = 20;

    private int port;
    private int sequenceNum = 1;
    private DatagramSocket datagramSocket;
    private State state = State.LISTEN;

    public MyServerSocket(int port) throws SocketException {
        datagramSocket = new DatagramSocket(port);
        datagramSocket.setSoTimeout(10000);

        this.port = port;

    }

    // TODO добавить создание сокета
    // TODO добавить отправку RST если все плохо
    public MySocket accept() {
        int try_num = 1;
        int clientPort;
        int clientSeq;
        int flags;
        InetAddress address = null;

        while (true) {
            byte[] buffer = new byte[HEADER_SIZE];
            DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);

            try {
                datagramSocket.receive(receivedPacket);
            } catch (SocketTimeoutException ex) {
                // на случай если таймаут был в состоянии SYN_RECEIVED
                state = State.LISTEN;
                try_num = 1;
                continue;
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            ByteBuffer data = ByteBuffer.wrap(receivedPacket.getData());
            if ((state == State.SYN_RECEIVED) && !receivedPacket.getAddress().equals(address)) {
                // если пришел пакет не от того, от кого ждем ответа
                if (try_num == 10) {
                    // если десять раз нам приходят пакеты не от того, от кого надо, забиваем на того, кого ждем
                    try_num = 1;
                    state = State.LISTEN;
                    continue;
                }

                try_num++;
                continue;
            }
            address = receivedPacket.getAddress();

            // парсим заголовок
            clientPort = data.getInt();
            data.getInt();
            clientSeq = data.getInt();
            data.getInt();
            flags = data.getInt();

            System.out.println(address + " " + clientPort + " " + clientSeq + " " + flags);
            if (((flags & SYN_FLAG) == SYN_FLAG) && (state == State.LISTEN)) {
                // новое соединение
                data.clear();

                data.putInt(port);
                data.putInt(clientPort);
                data.putInt(sequenceNum++);
                data.putInt(++clientSeq);
                data.putInt(SYN_FLAG | ACK_FLAG);

                DatagramPacket packet = new DatagramPacket(data.array(), data.capacity(), address, clientPort);

                try {
                    // отправляем syn ack пакет и переходим в состояние ожидания ответа
                    datagramSocket.send(packet);
                    state = State.SYN_RECEIVED;
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

            } else if (((flags & ACK_FLAG) == ACK_FLAG) && (state == State.SYN_RECEIVED)) {
                // получили ответ на наш syn ack, соединение установлено
                state = State.ESTABLISHED;
                System.out.println("Connection Established");
                return null;
            }
        }
    }
}
