package ru.nsu.fit.g15205.shishlyannikov;

import java.io.IOException;

public class ClientApp {
    public static void main( String[] args )
    {
        try {
            MySocket socket = new MySocket("localhost", 1111);

            socket.send("Hello World!".getBytes());

            byte[] buffer = new byte[120];
            int size = socket.receive(buffer);

            String recv = new String(buffer);
            recv = recv.substring(0, size);
            System.out.println(recv);

            //socket.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

}
