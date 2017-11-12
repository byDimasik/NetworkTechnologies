package ru.nsu.fit.g15205.shishlyannikov;

public class ServerApp {
    public static void main(String[] args) {
        String md5hash = null;
        int port;

        try {
            md5hash = args[0];
            port = Integer.valueOf(args[1]);

            System.out.println(md5hash);

            Server server = new Server(md5hash, port);
            server.start();
        } catch (IndexOutOfBoundsException | NumberFormatException ex) {
            System.err.println("Параметром передать md5 hash и порт");
            System.exit(0);
        }
    }
}
