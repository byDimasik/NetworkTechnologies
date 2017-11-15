package ru.nsu.fit.g15205.shishlyannikov;

/***
 * f1f8f4bf413b16ad135722aa4591043e - ACGT
 * c9ee3aba6598b0b16b0d955b0a5e654d - ACGTACGTA
 * 12ea00719389d64e5c7a2d49bc9d0db5 - 14 букв Т
 */
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
