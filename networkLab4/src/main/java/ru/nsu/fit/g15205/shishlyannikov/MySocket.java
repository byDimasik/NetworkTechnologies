package ru.nsu.fit.g15205.shishlyannikov;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class MySocket {
    private final byte SYN_FLAG = 1;
    private final byte ACK_FLAG = 2;
    private final byte FIN_FLAG = 4;
    private final byte RST_FLAG = 8;

    private final short HEADER_SIZE = 9;

    private DatagramSocket datagramSocket = null;
    private ArrayList<DatagramPacket> inPackets = new ArrayList<>();
    private ArrayList<DatagramPacket> outPackets = new ArrayList<>();

    private int sequenceNum = 1;
    private int destinationPort;

    private InetAddress destinationAddress;

    public MySocket() {}

    public MySocket(String host, int port) throws SocketException {
        datagramSocket = new DatagramSocket();
        datagramSocket.setSoTimeout(10000);

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

            data.putInt(sequenceNum++);
            data.putInt(0);
            data.put(SYN_FLAG);
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

            int serverSeq = data.getInt();
            int ack = data.getInt();
            byte flags = data.get();

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

        data.putInt(sequenceNum++);
        data.putInt(ackNum);
        data.put(ACK_FLAG);
        data.flip();

        DatagramPacket packet = new DatagramPacket(data.array(), data.capacity(), destinationAddress, destinationPort);

        try {
            datagramSocket.send(packet);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void addPacket(DatagramPacket packet) {
        inPackets.add(packet);
    }
}
