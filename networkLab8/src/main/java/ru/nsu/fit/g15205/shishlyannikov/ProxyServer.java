package ru.nsu.fit.g15205.shishlyannikov;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

public class ProxyServer {
    private Selector selector;
    private ServerSocketChannel serverSocketChannel;

    public ProxyServer(int port) {
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
        while (true) {
            try {
                int clientSelectNum = selector.select(300);

                if (clientSelectNum == 0) {
                    continue;
                }

                Set<SelectionKey> keys = selector.selectedKeys();    // ключики каналов, на которых есть данные
                Iterator<SelectionKey> iterator = keys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();

                    if (key.isAcceptable()) {
                        SocketChannel clientChannel = serverSocketChannel.accept();
                        System.err.println("Accept new client");
                        clientChannel.configureBlocking(false);
                        SelectionKey tmpKey = clientChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                        tmpKey.attach(new Node("Client", new Connection()));
                    } else if (key.isReadable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        Node node = (Node) key.attachment();
                        Connection connection = node.getConnection();

                        switch (connection.getState()) {
                            case WAIT_REQUEST:
                                if (node.getType().equals("Client")) {
                                    boolean code;
                                    try {
                                        code = receiveHeader(channel, connection);
                                    } catch (IOException ex) {
                                        channel.close();
                                        key.cancel();
                                        iterator.remove();
                                        continue;
                                    }

                                    if (code) {
                                        if (isSupportedMethod(connection.getMethod())) {
                                            String host = connection.getHost();
                                            int port = connection.getPort();
                                            if (-1 == port) port = 80;

                                            System.err.println("Try connect to " + host + ":" + port);
                                            try {
                                                SocketChannel serverChannel = SocketChannel.open(new InetSocketAddress(host, port));
                                                serverChannel.configureBlocking(false);
                                                SelectionKey tmpKey = serverChannel.register(selector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);
                                                tmpKey.attach(new Node("Server", connection));

                                                if (connection.getMethod().equals("POST")) {
                                                    connection.setState(ConnectionState.WAIT_BODY);
                                                } else {
                                                    connection.setState(ConnectionState.WRITE_REQUEST);
                                                }
                                                System.err.println("Successful connect to " + host + ":" + port);
                                            } catch (ConnectException ex) {
                                                System.err.println("Failed connect to " + host + ":" + port);
                                                channel.close();
                                                key.cancel();
                                                iterator.remove();
                                                continue;
                                            }
                                        }
                                    }
                                }

                                break;
                            case WAIT_BODY:
                                if (node.getType().equals("Client")) {
                                    if (connection.getContentLength() > 0) {
                                        int readCount;
                                        ByteBuffer buffer = ByteBuffer.allocate(connection.getContentLength());
                                        try {
                                            readCount = channel.read(buffer);
                                        } catch (IOException ex) {
                                            readCount = -1;
                                        }

                                        if (readCount == -1) {
                                            channel.close();
                                            key.cancel();
                                            iterator.remove();
                                            continue;
                                        }

                                        ByteBuffer reallySizeBuffer = ByteBuffer.allocate(readCount);
                                        reallySizeBuffer.put(Arrays.copyOf(buffer.array(), readCount));
                                        connection.addToBody(reallySizeBuffer);

                                        if (connection.getContentLength() <= 0) {
                                            connection.setState(ConnectionState.WRITE_REQUEST);
                                        }
                                    }
                                }
                                break;
                            case WAIT_RESPONSE:
                                if (node.getType().equals("Server")) {
                                    ByteBuffer buffer = ByteBuffer.allocate(10000);
                                    int readCount;
                                    try {
                                        readCount = channel.read(buffer);
                                    } catch (IOException ex) {
                                        readCount = -1;
                                    }

                                    if (readCount == -1) {
                                        System.err.println("Received response from " + connection.getHost());
                                        channel.close();
                                        key.cancel();
                                        iterator.remove();
                                        connection.setState(ConnectionState.WRITE_RESPONSE);
                                        continue;
                                    }

                                    ByteBuffer reallySizeBuffer = ByteBuffer.allocate(readCount);
                                    reallySizeBuffer.put(Arrays.copyOf(buffer.array(), readCount));
                                    connection.addToResponse(reallySizeBuffer);
                                }
                                break;
                            default:
                                break;
                        }
                    } else if (key.isWritable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        Node node = (Node) key.attachment();
                        Connection connection = node.getConnection();

                        switch (connection.getState()) {
                            case WRITE_RESPONSE:
                                if (node.getType().equals("Client")) {
                                    System.err.println("Write response");
                                    if (connection.getResponse() != null) {
                                        channel.write(connection.getResponse());
                                    }
                                    channel.close();
                                    key.cancel();
                                    iterator.remove();
                                    continue;
                                }
                                break;
                            case WRITE_REQUEST:
                                if (node.getType().equals("Server")) {
                                    System.err.println("Write request to " + connection.getHost());
                                    ByteBuffer headerBuffer = ByteBuffer.wrap(connection.getNewHeader().getBytes("UTF-8"));
                                    if (connection.getMethod().equals("POST")) {
                                        ByteBuffer request = ByteBuffer.allocate(headerBuffer.capacity() + connection.getBody().capacity());
                                        request.put(headerBuffer);
                                        request.put(connection.getBody());
                                        request.flip();
                                        channel.write(request);
                                    } else {
                                        channel.write(headerBuffer);
                                    }
                                    connection.setState(ConnectionState.WAIT_RESPONSE);
                                }
                                break;
                            default:
                                break;
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

    /*
     * На просторах интернета я нашел, что есть разные ограничения на header, а именно:
     * Apache: 8K
     * nginx: 4K - 8K
     * IIS: (varies by version): 8K - 16K
     * Tomcat (varies by version): 8K - 48K
     * Будем исходить из максимального варианта
     */
    private static int MAX_HEADER_SIZE = 48 * 1024;
    private boolean receiveHeader(SocketChannel channel, Connection connection) throws IOException {
        int code;

        ByteBuffer headerBuffer = ByteBuffer.allocate(MAX_HEADER_SIZE);

        try {
            code = channel.read(headerBuffer);
            headerBuffer.flip();
        } catch (IOException ex) {
            code = -1;
        }

        if (-1 == code) {
            throw new IOException("read return -1");
        }

        ByteBuffer reallySizeBuffer = ByteBuffer.allocate(code);
        reallySizeBuffer.put(Arrays.copyOf(headerBuffer.array(), code));
        connection.addToHeader(reallySizeBuffer);

        return connection.getState() == ConnectionState.HEADER_RECEIVED;
    }

    private boolean isSupportedMethod(String method) {
        return method.equals("GET") || method.equals("POST") || method.equals("HEAD");
    }
}
