package ru.nsu.fit.g15205.shishlyannikov;

import ru.nsu.fit.g15205.shishlyannikov.restServer.Server;

public class runServer {
    public static void main(String[] args) {
        Server server = new Server();
        server.start();
        System.out.println("Print \"exit\" to exit");
    }

}
