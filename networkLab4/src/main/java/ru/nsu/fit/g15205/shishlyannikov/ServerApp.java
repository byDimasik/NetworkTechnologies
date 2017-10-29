package ru.nsu.fit.g15205.shishlyannikov;

import java.net.SocketException;

public class ServerApp {
    public static void main( String[] args )
    {
        try {
            MyServerSocket serverSocket = new MyServerSocket(1111);
            serverSocket.accept();
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
    }
}
