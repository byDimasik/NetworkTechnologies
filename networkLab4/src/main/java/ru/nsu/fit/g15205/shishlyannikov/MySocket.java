package ru.nsu.fit.g15205.shishlyannikov;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;

public class MySocket {
    private final short SYN_FLAG = 1;
    private final short ACK_FLAG = 2;
    private final short FIN_FLAG = 4;
    private final short RST_FLAG = 8;

    private final short HEADER_SIZE = 20;

    private DatagramSocket datagramSocket;
    private int sequenceNum = 1;
    private int sourcePort;
    private int destinationPort;
    private InetAddress destinationAddress;


    // TODO спросить как сделать так, чтобы один и тот же класс MySocket был и для клиента и для сервера
    // TODO потому что на сервере нельзя больше одного UDP сокета, а на клиенте у каждого MySocket свой DatagramSocket
    public MySocket(String host, int port) throws SocketException {
        datagramSocket = new DatagramSocket();
        datagramSocket.setSoTimeout(10000);

        this.sourcePort = datagramSocket.getLocalPort();
        this.destinationPort = port;

        try {
            this.destinationAddress = InetAddress.getByName(host);
        } catch (UnknownHostException ex) {
            ex.printStackTrace();
        }

        try {
            connect();
        } catch (IOException ex) {
            System.err.println(ex.getLocalizedMessage());
        }
    }

    private void connect() throws IOException {
        while (true) {
            ByteBuffer data = ByteBuffer.allocate(HEADER_SIZE);

            data.putInt(sourcePort);
            data.putInt(destinationPort);
            data.putInt(sequenceNum++);
            data.putInt(0);
            data.putInt(SYN_FLAG);
            data.flip();

            DatagramPacket packet = new DatagramPacket(data.array(), data.capacity(), destinationAddress, destinationPort);

            try {
                datagramSocket.send(packet);

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

            if (data.getInt() != destinationPort) {
                // получили пакет не от того, от кого ждали
                continue;
            }
            data.getInt();  // пропускаем поле, в котором хранится наш порт

            int serverSeq = data.getInt();
            int ack = data.getInt();

            int flags = data.getInt();
            if ((flags & SYN_FLAG) == SYN_FLAG) {
                if (((flags & ACK_FLAG) == ACK_FLAG) && (ack == sequenceNum)) {
                    sendAck(++serverSeq);

                    System.out.println("Connection Established");
                    break;
                } else {
                    throw new IOException("Received SYN, but not ACK. Or ack num wrong");
                }
            } else if ((flags & RST_FLAG) == RST_FLAG) {
                throw new IOException("RST received");
            }

            try {
                Thread.sleep(10000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void sendAck(int ackNum) {
        ByteBuffer data = ByteBuffer.allocate(20);

        data.putInt(sourcePort);
        data.putInt(destinationPort);
        data.putInt(sequenceNum++);
        data.putInt(ackNum);
        data.putInt(ACK_FLAG);
        data.flip();

        DatagramPacket packet = new DatagramPacket(data.array(), data.capacity(), destinationAddress, destinationPort);

        try {
            datagramSocket.send(packet);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
