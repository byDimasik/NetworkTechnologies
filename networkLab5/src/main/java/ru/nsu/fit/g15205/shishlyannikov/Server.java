package ru.nsu.fit.g15205.shishlyannikov;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

/***
 * f1f8f4bf413b16ad135722aa4591043e - ACGT
 * c9ee3aba6598b0b16b0d955b0a5e654d - ACGTACGTA
 */
public class Server extends Thread {
    private final int MAX_LENGTH = 50;
    private final int STEP = 2;
    private final int TIMEOUT = 5000;

    private String alphabet = "ACGT";
    private String hashToBreaking;
    private Selector selector;
    private ServerSocketChannel serverSocketChannel;

    private Map<String, Integer> clientsWork = new HashMap<>();
    private Map<String, Long> clientsTime = new HashMap<>();

    private ArrayList<Integer> failedPeriods = new ArrayList<>();
    private int lastPeriod = 0;

    private boolean COMPLETE = false;

    public Server(String hash, int port) {
        hashToBreaking = hash;

        try {
            selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            InetSocketAddress address = new InetSocketAddress("localhost", port);
            serverSocketChannel.bind(address);
            serverSocketChannel.configureBlocking(false);

            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void run() {
        int num;
        boolean WORK = true;

        while (WORK || !clientsTime.isEmpty()) {
            try {
                long currentTime = System.currentTimeMillis();
                ArrayList<String> forDelete = new ArrayList<>();
                for (Map.Entry<String, Long> entry : clientsTime.entrySet()) {
                    if ((currentTime - entry.getValue()) > TIMEOUT) {
                        forDelete.add(entry.getKey());
                        failedPeriods.add(clientsWork.get(entry.getKey()));
                    }
                }

                for (String key : forDelete) {
                    clientsTime.remove(key);
                    clientsWork.remove(key);
                }

                int selectNum = selector.select(300);

                if (selectNum == 0) {
                    continue;
                }

                Set<SelectionKey> clientsKeys = selector.selectedKeys();    // ключики каналов, на которых есть данные
                Iterator<SelectionKey> iterator = clientsKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();

                    if (key.isAcceptable()) {
                        SocketChannel clientChannel = serverSocketChannel.accept();

                        clientChannel.configureBlocking(false);
                        clientChannel.register(selector, SelectionKey.OP_READ);
                        //System.out.println("Connection Accepted: " + clientChannel.getRemoteAddress() + "\n");
                    } else if (key.isReadable()) {
                        SocketChannel clientChannel = (SocketChannel) key.channel();

                        ByteBuffer buffer = ByteBuffer.allocate(10000);
                        try {
                            num = clientChannel.read(buffer);
                        } catch (IOException ex) {
                            num = -1;
                        }

                        if (num == -1) {
                            clientChannel.close();
                            key.cancel();
                            iterator.remove();
                            continue;
                        }

                        buffer.flip();

                        int len = buffer.getInt();
                        byte[] uuidByte = new byte[len];
                        buffer.get(uuidByte, 0, len);
                        String uuid = new String(uuidByte);

                        if (COMPLETE) {
                            WORK = false;

                            clientChannel.write(ByteBuffer.wrap("END".getBytes("UTF-8")));
                            clientChannel.close();
                            key.cancel();
                            iterator.remove();

                            if (clientsWork.containsKey(uuid)) {
                                clientsWork.remove(uuid);
                            }
                            if (clientsTime.containsKey(uuid)) {
                                clientsTime.remove(uuid);
                            }
                            continue;
                        }

                        if (clientsWork.containsKey(uuid)) {
                            clientsTime.put(uuid, System.currentTimeMillis());

                            byte[] flag = new byte[3];
                            buffer.get(flag, 0, 3);

                            if ("RES".equals(new String(flag))) {
                                int lenHash = buffer.getInt();
                                byte[] hashBytes = new byte[lenHash];
                                buffer.get(hashBytes);

                                if (hashToBreaking.equals(new String(hashBytes))) {
                                    int lenString = buffer.getInt();
                                    byte[] stringBytes = new byte[lenString];
                                    buffer.get(stringBytes);

                                    System.out.println("Искомая строка: " + new String(stringBytes));
                                    clientChannel.write(ByteBuffer.wrap("END".getBytes("UTF-8")));
                                    clientChannel.close();
                                    key.cancel();
                                    iterator.remove();

                                    clientsWork.remove(uuid);
                                    clientsTime.remove(uuid);

                                    WORK = false;
                                    COMPLETE = true;
                                    continue;
                                } else {
                                    clientChannel.write(ByteBuffer.wrap("CON".getBytes("UTF-8")));
                                    iterator.remove();
                                    continue;
                                }
                            }
                        }

                        if (!sendNewPeriod(uuid, clientChannel)) {
                            clientChannel.close();
                            key.cancel();
                            iterator.remove();
                            WORK = false;
                            continue;
                        }
                    }
                    iterator.remove();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                break;
            }
        }
    }

    private boolean sendNewPeriod(String uuid, SocketChannel clientChannel) {
        try {
            int period;

            if (!failedPeriods.isEmpty()) {
                period = failedPeriods.get(0);
                failedPeriods.remove(0);
            } else {
                period = ++lastPeriod;
            }

            if ((period * STEP) > MAX_LENGTH) {
                if (clientsTime.isEmpty()) {
                    COMPLETE = true;
                }
                clientChannel.write(ByteBuffer.wrap("END".getBytes("UTF-8")));
                return false;
            }

            clientsWork.put(uuid, period);
            clientsTime.put(uuid, System.currentTimeMillis());

            ByteBuffer buffer = ByteBuffer.allocate(3 + 4); // flag + period
            buffer.put("ANS".getBytes("UTF-8"));
            buffer.putInt(period);
            buffer.flip();

            clientChannel.write(buffer);

            return true;
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }
    }
}
