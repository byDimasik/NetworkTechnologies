package ru.nsu.fit.g15205.shishlyannikov;

import java.io.IOException;

public class ClientApp {
    public static void main(String args[]) {
        String serverAddress = null;
        int serverPort = 0;

        try {
            serverAddress = args[0];
            serverPort = Integer.valueOf(args[1]);

            Client client = new Client(serverAddress, serverPort);
            client.start();
        } catch (IndexOutOfBoundsException | NumberFormatException ex) {
            System.out.println("Нужно передать адрес и порт сервера");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
