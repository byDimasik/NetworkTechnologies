package ru.nsu.fit.g15205.shishlyannikov;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import sun.misc.Signal;
import sun.misc.SignalHandler;


public class Server {
    private static String OS = System.getProperty("os.name").toLowerCase();
    private static boolean WORK = true;
    private static final Object changeWorkState = new Object();

    public static void main(String[] args) {
        String addressStr = "localhost";                // адрес сервера
        String dir = "uploads";                         // имя директории для сохранения файлов
        int FIRST_MSG_SIZE = 5104;                      // sizeof(long) + (maxFileNameSize = 4096)
        int TIME_TO_SPEED = 3000;                       // период вывода скорости
        int port = 0;                                   // порт
        long startTime = System.currentTimeMillis();    // начальное время для расчета средней скорости
        long localTime = startTime;                     // начальное время для расчета мгновенной скорости
        long globalTime;                                // конечное время для расчета средней скорости
        long currentTime;                               // начальное время для расчета мгновенной скорости

        try {
             port = Integer.valueOf(args[0]);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.err.println("Надо было порт аргументом передать!");
            System.exit(1);
        }
        SignalHandler signalHandler = new SignalHandler() {
            @Override
            public void handle(Signal sig) {
                synchronized (changeWorkState) {
                    WORK = false;
                }
            }
        };
        DiagnosticSignalHandler.install("INT", signalHandler); // обработка SIGINT

        try {
            Selector selector = Selector.open();

            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            InetSocketAddress address = new InetSocketAddress(addressStr, port);

            serverSocketChannel.bind(address);
            serverSocketChannel.configureBlocking(false);

            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            Files.createDirectories(Paths.get(dir));

            while (true) {
                synchronized (changeWorkState) {
                    if (!WORK) {        // это может произойти только если серверу пришел SIGINT
                        break;
                    }
                }
                currentTime = (System.currentTimeMillis() - localTime); // считаем время для расчета мгновенной скорости
                globalTime = System.currentTimeMillis();                // для расчета глобальное скорости
                int selectNum = selector.select(300);           // ждем, пока на каналах появятся данные

                // если нет активности - ждем еще. Таймаут нужен на случай, если сервер вырубили
                if (selectNum == 0) {
                    continue;
                }

                Set<SelectionKey> clientsKeys = selector.selectedKeys();    // ключики каналов, на которых есть данные
                Iterator<SelectionKey> iterator = clientsKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();

                    if (key.isAcceptable()) {
                        // новому клиенту - новый канал
                        SocketChannel clientChannel = serverSocketChannel.accept();

                        clientChannel.configureBlocking(false);
                        clientChannel.register(selector, SelectionKey.OP_READ);
                        System.out.println("Connection Accepted: " + clientChannel.getRemoteAddress() + "\n");
                    } else if (key.isReadable()) {
                        SocketChannel clientChannel = (SocketChannel) key.channel();

                        // у старых клиентов есть аттачмент с инфой
                        if (key.attachment() != null) {
                            ClientConnection clientConnection = (ClientConnection) key.attachment();

                            ByteBuffer buffer = ByteBuffer.allocate(1024);
                            long num = clientChannel.read(buffer);
                            buffer.flip();

                            if (num != -1) {
                                // если что-то пришло, пишем это в файл
                                clientConnection.getFile().write(buffer.array(), 0, (int)num);
                                clientConnection.updateReceived(num);
                                if (currentTime > TIME_TO_SPEED) {
                                    // пришло время вывести скорости
                                    String speedMsg =
                                            clientChannel.getRemoteAddress() + ":" +
                                            "   Moment speed: " + clientConnection.resetReceived() / (currentTime / 1000) + " B/s" +
                                            "   Average speed: " + clientConnection.getRecorded() / ((globalTime - startTime) / 1000) + " B/s";
                                    System.out.println(speedMsg);
                                }
                            }
                            if ( num != 1024) {
                                // если пришло меньше килобайта, занчит пакет последний, заканчиваем работу
                                clientConnection.getFile().close();
                                if (!clientConnection.fileFull()) {
                                    // если файл записан не полностью, значит клиент помер, удаляем его файл
                                    System.out.println("Receive from " + clientChannel.getRemoteAddress() + " failed");
                                    Files.delete(clientConnection.getPathToFile());
                                }
                                else {
                                    System.out.println("File from " + clientChannel.getRemoteAddress() + " received\n");
                                    clientChannel.write(ByteBuffer.wrap("FR!".getBytes()));
                                }
                                clientChannel.close();
                                key.cancel();
                            }
                        } else {
                            // нет аттачмена, значит клиент у нас в первый раз, ждем имя и размер файла
                            ByteBuffer buffer = ByteBuffer.allocate(FIRST_MSG_SIZE);
                            clientChannel.read(buffer);
                            buffer.flip();

                            long fileSize = buffer.getLong();               // размер файла
                            int lenFileName = buffer.getInt();              // длина имени
                            byte[] byteFileName = new byte[lenFileName];
                            buffer.get(byteFileName);
                            String fileName = new String(byteFileName);     // само имя
                            String path = dir + "/" + fileName;             // путь на сервере

                            if (fileExist(path)) {
                                // если файл с таким именем уже есть, прощаемся с клиентом
                                System.out.println("Connection Cancelled: " + clientChannel.getRemoteAddress() + "\n");
                                clientChannel.write(ByteBuffer.wrap("FE!".getBytes()));
                                clientChannel.close();
                                key.cancel();
                                iterator.remove();
                                continue;
                            }

                            if (fileSize > getFreeSpace()) {
                                // если под файл не хватает места, также прощаемся с клиентом
                                System.out.println("Connection Cancelled: " + clientChannel.getRemoteAddress() + "\n");
                                clientChannel.write(ByteBuffer.wrap("NS!".getBytes()));
                                clientChannel.close();
                                key.cancel();
                                iterator.remove();
                                continue;
                            }

                            // если все хорошо, сообщаем о начале работы с клиентом
                            System.out.println("Size of file: " + fileSize + " File Name: " + fileName);

                            FileOutputStream file = new FileOutputStream(path);
                            ClientConnection clientConnection = new ClientConnection(Paths.get(path), fileSize, file);
                            key.attach(clientConnection);

                            buffer = ByteBuffer.wrap("OK!".getBytes());
                            clientChannel.write(buffer);
                        }
                    }
                    iterator.remove();
                }
                if (currentTime > TIME_TO_SPEED) {
                    // если прошло три секунды, значит, мы уже вывели скорость, обнуляем счетчик
                    localTime = System.currentTimeMillis();
                    System.out.println("");
                }
            }

            // сервер пытались убить сигинтом, сервер все понял, но сначала уберет за собой
            selector.select(300);
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = keys.iterator();

            // если остались клиенты, которые не закончили пересылку файлов, прощаемся с ними, удаляем их файлы
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                SocketChannel clientChannel = (SocketChannel) key.channel();
                ClientConnection clientConnection = (ClientConnection) key.attachment();
                if (clientConnection != null) {
                    clientConnection.getFile().close();
                    if (!clientConnection.fileFull()) {
                        Files.delete(clientConnection.getPathToFile());
                    }
                }
                clientChannel.close();
                key.cancel();
                iterator.remove();
            }
            selector.close();
        } catch (IOException ex) {
            System.err.println(ex.getLocalizedMessage());
        }
    }

    public static boolean fileExist(String path) {
        File checkName = new File(path);
        return (checkName.exists() && !checkName.isDirectory());
    }

    public static long getFreeSpace() {
        File checkFreeSpace;
        long freeSpace = 0;
        if (isWindows()) {
            checkFreeSpace = new File("c:");
            freeSpace = checkFreeSpace.getFreeSpace();
        } else if (isUnix()) {
            checkFreeSpace = new File("/");
            freeSpace = checkFreeSpace.getFreeSpace();
        } else {
            System.err.println("Unknown OS");
            System.exit(1);
        }
        return freeSpace;
    }

    public static boolean isWindows() {
        return OS.contains("win");
    }

    public static boolean isUnix() {
        return OS.contains("nix") || OS.contains("nux") || OS.contains("aix") || OS.contains("mac");
    }
}