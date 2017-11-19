package ru.nsu.fit.g15205.shishlyannikov;

import java.io.*;
import java.math.BigInteger;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class Client extends Thread {
    private final int CLIENT_MAX_LEN = 9;
    private final int UUID_LEN = 36;
    private final int HASH_LEN = 32;
    private final int MAX_PACKET_LENGTH = 3 + HASH_LEN;

    private SocketChannel socketChannel;
    private String uuid;

    private InetSocketAddress serverAddress;

    private String hashToBreaking = null;
    private String current_prefix;

    public Client(String serverAddress, int serverPort) throws IOException {
        uuid = UUID.randomUUID().toString();
        System.out.println("Start with uuid: " + uuid);

        this.serverAddress = new InetSocketAddress(serverAddress, serverPort);

        try {
            if (!tryConnect()) {
                throw new IOException("Не удалось подключиться к серверу!");
            }

            // отправляем uuid
            ByteBuffer buffer = ByteBuffer.allocate(UUID_LEN); // uuid
            buffer.put(uuid.getBytes("UTF-8"));
            buffer.flip();

            socketChannel.write(buffer);
            //----------------
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /***
     * Пытается 10 раз подключиться к серверу с перерывами в 3 секунды
     * @return успех - true, неудача - false
     */
    private boolean tryConnect() {
        for (int i = 0; i < 10; i++) {
            try {
                socketChannel = SocketChannel.open(serverAddress);
                return true;
            } catch (IOException ex) {
                socketChannel = null;
                sleep(3000);
            }
        }

        return false;
    }

    public void run() {
        int num;
        boolean WORK = true;

        while (WORK) {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(MAX_PACKET_LENGTH);

                try {
                    num = socketChannel.read(buffer);
                } catch (ConnectException ex) {
                    num = -1;
                }

                if (num == -1) {
                    socketChannel.close();
                    System.out.println("Сервер помер");
                    break;
                }

                buffer.flip();

                byte[] ansByte = new byte[3];
                try {
                    buffer.get(ansByte, 0, 3);
                } catch (BufferUnderflowException ex) {
                    send_task_request();
                    continue;
                }
                String answer = new String(ansByte);

                switch (answer) {
                    case "END":
                        // работы больше нет
                        System.err.println("Работы больше нет.");
                        socketChannel.close();
                        WORK = false;
                        continue;
                    case "ANS":
                        // новая задача
                        int prefixLen = buffer.getInt();
                        if (prefixLen == 0) {
                            current_prefix = "";
                            System.err.println("New task: empty prefix");
                        } else {
                            byte[] prefixByte = new byte[prefixLen];
                            try {
                                buffer.get(prefixByte);
                            } catch (BufferUnderflowException ex) {
                                send_task_request();
                                continue;
                            }
                            current_prefix = new String(prefixByte);
                            System.err.println("New task: received new prefix: " + current_prefix);
                        }
                        socketChannel.close();
                        break;
                    case "HSH":
                        if (hashToBreaking != null) {
                            // сервер нас забыл, пока мы считали
                            continue;
                        }
                        // получаем хэш
                        byte[] hashByte = new byte[HASH_LEN];
                        try {
                            buffer.get(hashByte);
                        } catch (BufferUnderflowException ex) {
                            throw new IOException("Хэш пришел не полностью");
                        }
                        hashToBreaking = new String(hashByte);
                        System.err.println("Hash to breaking: " + hashToBreaking);

                        continue;
                    default:
                        socketChannel.close();
                        WORK = false;
                        continue;
                }

                String res = bruteForce();
                if (res == null) {
                    System.err.print("Bruteforce failed. ");
                } else {
                    System.err.println("Bruteforce success. Result: " + res);
                }

                if (!tryConnect()) {
                    System.err.println("Не удалось подключиться к серверу!");
                    WORK = false;
                    continue;
                }

                if (res != null) {
                    // нашли строку, отправляем результат
                    buffer = ByteBuffer.allocate(UUID_LEN + 3 + 4 + res.length());

                    buffer.put(uuid.getBytes("UTF-8"));
                    buffer.put("SUC".getBytes("UTF-8"));
                    buffer.putInt(res.length());
                    buffer.put(res.getBytes("UTF-8"));

                    buffer.flip();
                    socketChannel.write(buffer);

                    socketChannel.close();
                    WORK = false;
                } else {
                    // не нашли строку, просим новую задачу
                    buffer = ByteBuffer.allocate(UUID_LEN + 3);

                    buffer.put(uuid.getBytes("UTF-8"));
                    buffer.put("NXT".getBytes("UTF-8"));
                    buffer.flip();

                    System.err.println("New task requested.");
                    socketChannel.write(buffer);
                }
            } catch (IOException ex) {
                System.err.println(ex.getLocalizedMessage());
                break;
            }
        }
    }

    /***
     * Возвращает md5 хэш строки str
     */
    private String getHash(String str) {
        try {
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.reset();

            m.update(str.getBytes("utf-8"));

            // получаем MD5-хеш строки без лидирующих нулей
            String s2 = new BigInteger(1, m.digest()).toString(16);
            StringBuilder sb = new StringBuilder(32);

            // дополняем нулями до 32 символов, в случае необходимости
            for (int i = 0, count = 32 - s2.length(); i < count; i++) {
                sb.append("0");
            }

            return sb.append(s2).toString();
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException ex) {
            ex.printStackTrace();
        }

        return null;
    }

    /***
     * Брутфорсит 4^CLIENT_MAX_LEN строк, которые начинаются с префикса current_prefix
     */
    private String bruteForce() {
        StringBuilder stringBuilder = new StringBuilder();

        if (current_prefix.length() == 0) {
            // если префикс пустой, считаем все строки до длины 5
            stringBuilder.append(Alphabet.getFirstSymb());
        } else {
            // иначе пропускаем все строки длины меньше префикс + CLIENT_MAX_LEN
            stringBuilder.append(current_prefix);
            for (int i = 0; i < CLIENT_MAX_LEN; i++) {
                stringBuilder.append(Alphabet.getFirstSymb());
            }
        }

        String current = stringBuilder.toString();

        String hash;
        for (int i = 0; i < Math.pow(4, CLIENT_MAX_LEN); i++) {
//            if (current_prefix.equals("GGGGCTG"))
//                System.out.println(current);
            hash = getHash(current);
            if (hashToBreaking.equals(hash)) {
                return current;
            }
            current = Alphabet.getNextWord(current);
        }

        return null;
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    private void send_task_request() {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(UUID_LEN + 3);

            buffer.put(uuid.getBytes("UTF-8"));
            buffer.put("NXT".getBytes("UTF-8"));
            buffer.flip();

            socketChannel.write(buffer);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
