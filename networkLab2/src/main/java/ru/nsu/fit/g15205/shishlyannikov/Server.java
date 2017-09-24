package ru.nsu.fit.g15205.shishlyannikov;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


public class Server {

    @SuppressWarnings("unused")
    public static void main(String[] args) throws IOException {
        Map<SelectionKey, FileOutputStream> clients = new TreeMap<>();

        Selector selector = Selector.open(); // selector is open here

        // ServerSocketChannel: selectable channel for stream-oriented listening sockets
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        InetSocketAddress address = new InetSocketAddress("localhost", 1111);

        serverSocketChannel.bind(address);
        serverSocketChannel.configureBlocking(false);

        int ops = serverSocketChannel.validOps();
        SelectionKey selectKy = serverSocketChannel.register(selector, ops, null);

        while (true) {

            // Selects a set of keys whose corresponding channels are ready for I/O operations
            selector.select();

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
                    ByteBuffer buffer = ByteBuffer.allocate(5104); // 8 + 4096 : sizeof(long) + maxFileNameSize
                    clientChannel.read(buffer);

                    if (clients.containsKey(key)) {
                        //принимаем кусок файла
                    } else {  //TODO ответ клиента, добавление клиента
                        DataInputStream firstMsg = new DataInputStream(new ByteArrayInputStream(buffer.array()));
                        long fileSize = firstMsg.readLong();
                        String result = firstMsg.readUTF();

                        log("Size of file: " + fileSize + " File Name: " + result);
                        clientChannel.close();
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