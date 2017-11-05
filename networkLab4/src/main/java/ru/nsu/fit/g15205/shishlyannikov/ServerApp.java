package ru.nsu.fit.g15205.shishlyannikov;

import java.io.IOException;

public class ServerApp {
    public static void main( String[] args )
    {
        try {
            MyServerSocket serverSocket = new MyServerSocket(1111);
            MySocket socket = serverSocket.accept();

            byte[] buffer = new byte[50];
            int size = socket.receive(buffer);
            String recv = new String(buffer);
            recv = recv.substring(0, size);

            recv = recv.toUpperCase();

            socket.send(recv.getBytes());

            socket.close();

            System.out.println(serverSocket.close());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
