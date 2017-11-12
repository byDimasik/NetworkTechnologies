package ru.nsu.fit.g15205.shishlyannikov;

import java.io.*;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;

public class Client extends Thread {
    private final String alphabet = "ACGT";
    private final int STEP = 2;

    private SocketChannel socketChannel;
    private String uuid;

    private InetSocketAddress serverAddress;

    private Work work = null;

    public Client(String serverAddress, int serverPort) {
        uuid = UUID.randomUUID().toString();
        System.out.println("Start with uuid: " + uuid);

        this.serverAddress = new InetSocketAddress(serverAddress, serverPort);

        try {
            socketChannel = SocketChannel.open(this.serverAddress);

            ByteBuffer buffer = ByteBuffer.allocate(4 + uuid.length()); // len(uuid) + uuid
            buffer.putInt(uuid.getBytes().length);
            buffer.put(uuid.getBytes("UTF-8"));
            buffer.flip();

            socketChannel.write(buffer);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void run() {
        int num;
        boolean WORK = true;

        while (WORK) {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(3 + 4);

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
                buffer.get(ansByte, 0, 3);
                String answer = new String(ansByte);

                switch (answer) {
                    case "END":
                        socketChannel.close();
                        WORK = false;
                        continue;
                    case "ANS":
                        int period = buffer.getInt();
                        work = new Work((period * STEP) - STEP + 1, period * STEP);
                        socketChannel.close();
                        break;
                    default:
                        System.err.println("ВСЕ В ЛЮТОМ ГОВНИЩЕ АЖ ВОНЯЕТ Я ПРЯМ ПЕРЕД МОНИТОРОМ СИДЯ ПОЧУСТВОВАЛ!!!");
                        socketChannel.close();
                        WORK = false;
                        continue;
                }

                bruteForce();

                try {
                    socketChannel = SocketChannel.open(this.serverAddress);

                    for (Map.Entry<String, String> entry : work.getResults().entrySet()) {
                        buffer = ByteBuffer.allocate(
                                4 + uuid.getBytes("UTF-8").length +
                                3 +
                                4 + entry.getKey().getBytes("UTF-8").length +
                                4 + entry.getValue().getBytes("UTF-8").length);

                        buffer.putInt(uuid.getBytes().length);
                        buffer.put(uuid.getBytes("UTF-8"));

                        buffer.put("RES".getBytes("UTF-8"));

                        buffer.putInt(entry.getKey().getBytes().length);
                        buffer.put(entry.getKey().getBytes());

                        buffer.putInt(entry.getValue().getBytes().length);
                        buffer.put(entry.getValue().getBytes());
                        buffer.flip();

                        socketChannel.write(buffer);

                        buffer = ByteBuffer.allocate(3);

                        try {
                            num = socketChannel.read(buffer);
                        } catch (IOException ex) {
                            num = -1;
                        }

                        if (num == -1) {
                            socketChannel.close();
                            System.out.println("Сервер помер");
                            break;
                        }
                        buffer.flip();

                        ansByte = new byte[3];
                        try {
                            buffer.get(ansByte, 0, 3);
                        } catch (BufferUnderflowException ex) {
                            System.out.println(new String(buffer.array()));
                        }
                        answer = new String(ansByte);

                        if ("END".equals(answer)) {
                            socketChannel.close();
                            WORK = false;
                            break;
                        }
                    }

                    if (!WORK) {
                        break;
                    }

                    buffer = ByteBuffer.allocate(4 + uuid.getBytes("UTF-8").length + 3);

                    buffer.putInt(uuid.getBytes().length);
                    buffer.put(uuid.getBytes("UTF-8"));

                    buffer.put("END".getBytes("UTF-8"));

                    buffer.flip();

                    socketChannel.write(buffer);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                break;
            }
        }
    }

    private int countChars(String string, char ch) {
        int count = 0;
        for (int i = 0; i < string.length(); i++) {
            if (string.charAt(i) == ch) {
                count++;
            }
        }
        return count;
    }

    private String getNextWord(String string) {
        String alphabet = "ACGT";

        StringBuilder stringBuilder = new StringBuilder(string);

        if (countChars(string, alphabet.charAt(alphabet.length()-1)) == string.length()) {
            stringBuilder = new StringBuilder();
            for (int i = 0; i < string.length()+1; i++) {
                stringBuilder.append(alphabet.charAt(0));
            }
            return stringBuilder.toString();
        }

        for (int i = string.length() - 1; i > -1; i--) {
            if (string.charAt(i) == alphabet.charAt(alphabet.length() - 1)) {
                stringBuilder.setCharAt(i, alphabet.charAt(0));
            } else {
                int cur_index = alphabet.indexOf(string.charAt(i));
                stringBuilder.setCharAt(i, alphabet.charAt(++cur_index));
                return stringBuilder.toString();
            }
        }

        return null;
    }

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

    private void bruteForce() {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < work.getStart(); i++) {
            stringBuilder.append(alphabet.charAt(0));
        }

        String current = stringBuilder.toString();

        String hash;
        while (current.length() <= work.getEnd()) {
            hash = getHash(current);
            work.addResult(hash, current);
            current = getNextWord(current);
        }
    }
}
