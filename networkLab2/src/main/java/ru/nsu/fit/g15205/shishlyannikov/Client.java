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

//        System.out.println(path);
//        System.out.println(fileName);
//        System.out.println("File size = " + file.getChannel().size());
//        System.out.println(stringAddress);
//        System.out.println(port);

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
        buffer.clear();
//        while (true){
//
//            // wait for 2 seconds before sending next message
//            Thread.sleep(2000);
//        }

        clientChannel.close();

    }

    private static void sendMessage(String msg) throws IOException {
        byte[] message = msg.getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(message);
        clientChannel.write(buffer);
        buffer.clear();
    }
}
