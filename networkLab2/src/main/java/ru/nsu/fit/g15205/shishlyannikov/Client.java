package ru.nsu.fit.g15205.shishlyannikov;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.NoSuchFileException;

public class Client {
    private static SocketChannel clientChannel;
    private static int FIRST_MSG_SIZE = 5104; // sizeof(long) + (maxFileNameSize = 4096)

    public static void main(String[] args) {
        String path = "";
        String stringAddress = "";
        int port = 0;

        try {
            path = args[0];
            stringAddress = args[1];
            port = Integer.valueOf(args[2]);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.err.println("Надо передать аргументы! имя_файла адрес_сервера порт\nБез них не заработает.");
            System.exit(1);
        }

        try {
            FileInputStream file = new FileInputStream(path);

            String fileName = path.substring(path.lastIndexOf("/") + 1);

            InetSocketAddress address = new InetSocketAddress(stringAddress, port);
            clientChannel = SocketChannel.open(address);

            // Send size of file and file name
            long fileSize = file.getChannel().size();


            ByteBuffer buffer = ByteBuffer.allocate(FIRST_MSG_SIZE);
            buffer.putLong(fileSize);
            buffer.putInt(fileName.length());
            buffer.put(fileName.getBytes());
            buffer.flip();

            clientChannel.write(buffer);
            //--------------------------------

            boolean WORK = true;
            while (WORK) {
                buffer = ByteBuffer.allocate(3);
                clientChannel.read(buffer);

                String answer = new String(buffer.array());
                switch (answer) {
                    case "OK!":
                        System.out.println("Соединение установлено. Начало передачи файла.");
                        sendFile(file);
                        break;
                    case "FR!":
                        System.out.println("Файл успешно передан!");
                        WORK = false;
                        break;
                    case "NS!":
                        System.out.println("На сервере нет места для файла!");
                        WORK = false;
                        break;
                    case "FE!":
                        System.out.println("Файл с таким именем уже существует!");
                        WORK = false;
                        break;
                    default:
                        System.out.println("Не удалось подключиться к серверу!");
                        WORK = false;
                        break;
                }
            }

            clientChannel.close();
        } catch (FileNotFoundException ex) {
            System.err.println("Нет такого файла, какой ты хочешь передать!");
        } catch (IOException ex) {
            System.err.println("Сервер помер. RIP In Peace. " + ex.getLocalizedMessage());
        }
    }

    private static void sendFile(FileInputStream file) throws IOException {
        byte[] filePart = new byte[1024];
        int num;
        while ( (num = file.read(filePart, 0, filePart.length)) != -1) {
            ByteBuffer buffer = ByteBuffer.allocate(num);
            buffer.put(filePart, 0, num);
            buffer.flip();
            clientChannel.write(buffer);
        }
    }
}
