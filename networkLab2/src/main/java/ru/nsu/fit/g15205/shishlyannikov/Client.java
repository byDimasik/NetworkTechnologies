package ru.nsu.fit.g15205.shishlyannikov;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class Client {
    private static SocketChannel clientChannel;

    public static void main(String[] args) throws IOException, InterruptedException {
        String path = args[0];
        String stringAddress = args[1];
        int port = Integer.valueOf(args[2]);

        FileInputStream file = new FileInputStream(path);

        String fileName = path.substring(path.lastIndexOf("/") + 1);

        InetSocketAddress address = new InetSocketAddress(stringAddress, port);
        clientChannel = SocketChannel.open(address);

        // Send size of file and file name
        long fileSize = file.getChannel().size();

        ByteArrayOutputStream firstMsg = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(firstMsg);

        dataOutputStream.writeLong(fileSize);
        dataOutputStream.writeUTF(fileName);

        ByteBuffer buffer = ByteBuffer.wrap(firstMsg.toByteArray());
        clientChannel.write(buffer);
        //--------------------------------

        // receive answer
        buffer = ByteBuffer.allocate(3);
        clientChannel.read(buffer);

        String answer = new String(buffer.array());
        System.out.println(answer);
        if ( answer.equals("OK!") ) {
            sendFile(file);
        }
        else {
            System.out.println("Не удалось подключиться к серверу!");
            clientChannel.close();
        }

        clientChannel.close();

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
