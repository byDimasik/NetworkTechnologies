package ru.nsu.fit.g15205.shishlyannikov;

import java.net.SocketException;

public class ClientApp {
    public static void main( String[] args )
    {
        try {
            MySocket socket = new MySocket("localhost", 1111);
        } catch (SocketException ex) {
            ex.printStackTrace();
        }

    }

}
