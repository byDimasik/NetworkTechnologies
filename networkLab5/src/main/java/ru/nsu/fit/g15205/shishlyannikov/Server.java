package ru.nsu.fit.g15205.shishlyannikov;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

public class Server extends Thread {
    private final int MAX_LENGTH = 15;
    private final int CLIENT_MAX_LEN = 9;
    private final int TIMEOUT = 5000;
    private final int UUID_LEN = 36;
    private final int MAX_PACKET_SIZE = UUID_LEN + 3 + 4 + MAX_LENGTH;

    private String prefix = "";

    private String hashToBreaking;
    private Selector selector;
    private ServerSocketChannel serverSocketChannel;

    private Map<String, String> clientsWork = new HashMap<>(); // кому какую работу дали
    private Map<String, Long> clientsTime = new HashMap<>();   // кто когда последний раз проявлял активность

    private ArrayList<String> failedWorks = new ArrayList<>();

    private boolean COMPLETE = false;
    private boolean END_WORK = false;

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

        // пока не найдем строку или, если работы больше нет, пока не доработают все клиенты
        while (!COMPLETE || !clientsTime.isEmpty()) {

            try {
                // удаляем тех, кто помер (не отвечал 5 секунд)
                long currentTime = System.currentTimeMillis();

                ArrayList<String> forDelete = new ArrayList<>();
                for (Map.Entry<String, Long> entry : clientsTime.entrySet()) {
                    if ((currentTime - entry.getValue()) > TIMEOUT) {
                        forDelete.add(entry.getKey());
                        failedWorks.add(clientsWork.get(entry.getKey())); // добавляем работу в список невыполненных
                    }
                }

                for (String key : forDelete) {
                    clientsTime.remove(key);
                    clientsWork.remove(key);
                }

                if (END_WORK && clientsTime.isEmpty()) {
                    // если работы нет и все клиенты отвалились или закончиили работу
                    break;
                }
                //----------------------------------------------

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
                        clientChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    } else if (key.isReadable() && key.isWritable()) {
                        SocketChannel clientChannel = (SocketChannel) key.channel();

                        ByteBuffer buffer = ByteBuffer.allocate(MAX_PACKET_SIZE);
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

                        // сначала всегда uuid клиента
                        byte[] uuidByte = new byte[UUID_LEN];
                        try {
                            buffer.get(uuidByte, 0, UUID_LEN);
                        } catch (BufferUnderflowException ex) {
                            iterator.remove();
                            continue;
                        }
                        String uuid = new String(uuidByte, "UTF-8");

                        if (COMPLETE) {
                            // если мы уже все посчитали, говорим клиенту завершиться
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
                            // если ждем от клиента работы
                            clientsTime.put(uuid, System.currentTimeMillis());

                            byte[] flag = new byte[3];
                            buffer.get(flag, 0, 3);

                            if ("SUC".equals(new String(flag, "UTF-8"))) {
                                // если клиент нашел ответ
                                int lenRes = buffer.getInt();
                                byte[] resBytes = new byte[lenRes];
                                try {
                                    buffer.get(resBytes);
                                } catch (BufferUnderflowException ex) {
                                    iterator.remove();
                                    continue;
                                }

                                System.err.println("Искомая строка: " + new String(resBytes));

                                clientChannel.close();
                                key.cancel();
                                iterator.remove();

                                clientsWork.remove(uuid);
                                clientsTime.remove(uuid);

                                COMPLETE = true;
                                continue;
                            }
                        } else {
                            // если клиент первый раз - отправляем ему хэш
                            buffer = ByteBuffer.allocate(3 + hashToBreaking.length());
                            buffer.put("HSH".getBytes("UTF-8"));
                            buffer.put(hashToBreaking.getBytes("UTF-8"));
                            buffer.flip();
                            clientChannel.write(buffer);
                        }

                        // отправляем новую задачу клиенту
                        if (!sendNewPrefix(uuid, clientChannel)) {
                            clientChannel.close();
                            key.cancel();
                            iterator.remove();
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

        if (!COMPLETE) {
            System.err.println("Не удалось взломать md5 хэш");
        }
    }

    private boolean sendNewPrefix(String uuid, SocketChannel clientChannel) {
        try {
            String current_prefix;

            if (!failedWorks.isEmpty()) { // если есть невыполненные работы - они в приоритете
                current_prefix = failedWorks.get(0);
                failedWorks.remove(0);
            } else {
                current_prefix = prefix;
                prefix = Alphabet.getNextWord(prefix); // следующий префикс по алфавиту
            }

            if (current_prefix.length() > (MAX_LENGTH - CLIENT_MAX_LEN)) {
                // если префикс стал максимальной длины - значит все уже перебрали, конец работы
                END_WORK = true;
                clientChannel.write(ByteBuffer.wrap("END".getBytes("UTF-8")));
                if (clientsWork.containsKey(uuid)) {
                    clientsWork.remove(uuid);
                }
                if (clientsTime.containsKey(uuid)) {
                    clientsTime.remove(uuid);
                }
                return false;
            }

            clientsWork.put(uuid, current_prefix);
            clientsTime.put(uuid, System.currentTimeMillis());

            ByteBuffer buffer = ByteBuffer.allocate(3 + 4 + current_prefix.length()); // flag + period
            buffer.put("ANS".getBytes("UTF-8"));
            buffer.putInt(current_prefix.length());
            if (current_prefix.length() != 0) {
                buffer.put(current_prefix.getBytes("UTF-8"));
            }
            buffer.flip();

            System.err.println("send prefix " + current_prefix + " to " + uuid);
            clientChannel.write(buffer);

            return true;
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }
    }
}
