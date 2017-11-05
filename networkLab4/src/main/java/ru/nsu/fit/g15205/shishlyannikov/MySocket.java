package ru.nsu.fit.g15205.shishlyannikov;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

public class MySocket {
    enum State { CLOSED, ESTABLISHED, FIN_SENT }
    private final byte SYN_FLAG = 1;
    private final byte ACK_FLAG = 2;
    private final byte FIN_FLAG = 4;
    private final byte RST_FLAG = 8;

    private final short HEADER_SIZE = 9;
    private final short MAX_PACKET_SIZE = HEADER_SIZE + 1024;

    private DatagramSocket datagramSocket = null;
    private BlockingQueue<DatagramPacket> inPackets = new LinkedBlockingDeque<>();
    private ConcurrentHashMap<Integer, DatagramPacket> outPackets = new ConcurrentHashMap<>();
    private BlockingQueue<DatagramPacket> acks = new LinkedBlockingDeque<>();

    private int sequenceNum = 1;
    private int expectedSeqNum;
    private int destinationPort;

    private InetAddress destinationAddress;
    private State state = State.CLOSED;

    private Thread sender;
    private Thread receiver;

    private final Object obj = new Object();

    public MySocket(InetAddress host, int port, int clientSeq) {
        destinationAddress = host;

        destinationPort = port;
        expectedSeqNum = clientSeq;
        state = State.ESTABLISHED;
    }

    public MySocket(String host, int port) throws SocketException {
        datagramSocket = new DatagramSocket();
        datagramSocket.setSoTimeout(10000);

        this.destinationPort = port;

        try {
            this.destinationAddress = InetAddress.getByName(host);
        } catch (UnknownHostException ex) {
            ex.printStackTrace();
        }

        receiver = new Thread(() -> {
            synchronized (obj) {
                try {
                    obj.wait();
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }

            while (state != State.FIN_SENT || !outPackets.isEmpty()) {
                try {
                    byte[] buffer = new byte[MAX_PACKET_SIZE];
                    DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);

                    datagramSocket.receive(receivedPacket);

                    ByteBuffer data = ByteBuffer.wrap(receivedPacket.getData());
                    int seq = data.getInt();
                    int ackNum = data.getInt();
                    byte flags = data.get();

                    if (seq != expectedSeqNum) {
                        if (seq < expectedSeqNum) {
                            sendAck(++seq);
                        }
                        continue;
                    }

                    expectedSeqNum++;

                    if ((flags & ACK_FLAG) == ACK_FLAG) {
                        removePacket(ackNum);
                        continue;
                    }

                    sendAck(++seq);

                    if ((flags & RST_FLAG) == RST_FLAG) {
                        state = State.FIN_SENT;
                        continue;
                    }

                    if (state != State.FIN_SENT) {
                        inPackets.add(receivedPacket);
                    }
                } catch (SocketTimeoutException ex) {
                    System.out.println("timeout");
                    continue;
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        receiver.start();

        sender = new Thread(() -> {
            while (state != State.FIN_SENT || !outPackets.isEmpty()) {
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
                    expectedSeqNum = serverSeq;

                    state = State.ESTABLISHED;
                    synchronized (obj) {
                        obj.notify();
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
                Thread.sleep(10000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

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

    public void addPacket(DatagramPacket packet) {
        inPackets.add(packet);
    }

    public int receive(byte[] buffer) {
        if (state == State.FIN_SENT) {
            return 0;
        }

        while (true) {
            try {
                DatagramPacket packet = inPackets.take();

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

    public void send(byte[] buffer) throws SocketException {
        if (state == State.FIN_SENT) {
            throw new SocketException("socket closed");
        }

        int size;

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

        acks.drainTo(ret);

        return ret;
    }

    public void removePacket(int ackNum) {
        if (outPackets.containsKey(ackNum)) {
            outPackets.remove(ackNum);
        }
        if (state == State.FIN_SENT && outPackets.isEmpty()) {
            synchronized (obj) {
                obj.notify();
            }
        }
    }

    public boolean close() throws IOException {
        if (state == State.FIN_SENT) {
            return true;
        }
        state = State.FIN_SENT;

        if (datagramSocket == null) {
            ByteBuffer data = ByteBuffer.allocate(HEADER_SIZE);

            data.putInt(sequenceNum++);
            data.putInt(0);
            data.put(RST_FLAG);

            DatagramPacket packet = new DatagramPacket(data.array(), data.capacity(), destinationAddress, destinationPort);

            outPackets.put(sequenceNum, packet);

            synchronized (obj) {
                try {
                    obj.wait(10000);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }

            if (!isClosed()) {
                outPackets.clear();
                return false;
            }

            System.out.println("Socket closed");
            return true;
        }

        try {
            receiver.join();
            sender.join();
        } catch (InterruptedException ex) {
            throw new IOException("interrupted");
        }

        ByteBuffer data = ByteBuffer.allocate(HEADER_SIZE);

        data.putInt(sequenceNum++);
        data.putInt(0);
        data.put(FIN_FLAG);

        DatagramPacket packet = new DatagramPacket(data.array(), data.capacity(), destinationAddress, destinationPort);

        datagramSocket.send(packet);

        while (true) {
            byte[] buffer = new byte[HEADER_SIZE];

            DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);

            try {
                datagramSocket.receive(receivedPacket);

                data = ByteBuffer.wrap(receivedPacket.getData());

                int seqNum = data.getInt();
                int ack = data.getInt();
                byte flags = data.get();

                if (((flags & FIN_FLAG) == FIN_FLAG) && ((flags & ACK_FLAG) == ACK_FLAG) && (ack == sequenceNum)) {
                    sendAck(++seqNum);
                    return true;
                }
            } catch (SocketTimeoutException ex) {
                return false;
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
        state = State.FIN_SENT;
    }

    public boolean isClosed() {
        return (state == State.FIN_SENT) && inPackets.isEmpty() && outPackets.isEmpty();
    }
}
