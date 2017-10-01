package ru.nsu.fit.g15205.shishlyannikov;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


public class Server {
    static String dir = "uploads";

    @SuppressWarnings("unused")
    public static void main(String[] args) throws IOException {
        int port = Integer.valueOf(args[0]);

        Map<String, FileOutputStream> clients = new TreeMap<>();

        Selector selector = Selector.open(); // selector is open here

        // ServerSocketChannel: selectable channel for stream-oriented listening sockets
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        InetSocketAddress address = new InetSocketAddress("localhost", port);

        serverSocketChannel.bind(address);
        serverSocketChannel.configureBlocking(false);

        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        Path path = Paths.get(dir);
        Files.createDirectories(path);
        while (true) {

            // Selects a set of keys whose corresponding channels are ready for I/O operations
            int selectNum = selector.select();

            // если нет активности - ждем
            if (selectNum == 0) {
                continue;
            }


            // token representing the registration of a SelectableChannel with a Selector
            Set<SelectionKey> cleintsKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = cleintsKeys.iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();

                // Tests whether this key's channel is ready to accept a new socket connection
                if (key.isAcceptable()) {
                    SocketChannel clientChannel = serverSocketChannel.accept();

                    clientChannel.configureBlocking(false);
                    clientChannel.register(selector, SelectionKey.OP_READ);
                    System.out.println("Connection Accepted: " + clientChannel.getLocalAddress() + "\n");
                } else if (key.isReadable()) {
                    SocketChannel clientChannel = (SocketChannel) key.channel();
                    String clientName = clientChannel.toString();

                    if (clients.containsKey(clientName)) {
                        ByteBuffer buffer = ByteBuffer.allocate(1024);
                        long num = clientChannel.read(buffer);
                        buffer.flip();
                        if (num != -1) {
                            clients.get(clientName).write(buffer.array(), 0, (int)num);
                        }
                        if ( num != 1024) {
                            System.out.println("File from " + clientChannel.getLocalAddress() + " received\n");

                            clientChannel.close();
                            clients.get(clientName).close();
                            clients.remove(clientName);
                        }

                    } else {
                        ByteBuffer buffer = ByteBuffer.allocate(5104); // 8 + 4096 : sizeof(long) + maxFileNameSize
                        clientChannel.read(buffer);

                        DataInputStream firstMsg = new DataInputStream(new ByteArrayInputStream(buffer.array()));
                        long fileSize = firstMsg.readLong();
                        String fileName = firstMsg.readUTF();

                        log("Size of file: " + fileSize + " File Name: " + fileName);

                        FileOutputStream file = new FileOutputStream(dir + "/" + fileName);
                        clients.put(clientName, file);

                        buffer = ByteBuffer.wrap("OK!".getBytes());
                        clientChannel.write(buffer);
                    }
                }
                iterator.remove();
            }
        }
    }

    private static void log(String str) {
        System.out.println(str);
    }
}