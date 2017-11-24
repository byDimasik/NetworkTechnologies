package ru.nsu.fit.g15205.shishlyannikov;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class Client {
    public static void main(String[] args) throws InterruptedException {

        try (Socket socket = new Socket("localhost", 1111);
             BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream()) ) {

            socket.setSoTimeout(500);
            String clientCommand = "POST /login HTTP/1.1\r\n" +
                                   "Content-Type: application/json\r\n";

            while (true) {
                if (br.ready()) {
                    clientCommand = br.readLine();
                    if (clientCommand != null) {
                        if (clientCommand.equalsIgnoreCase("exit")) {
                            break;
                        }

                        out.writeUTF(clientCommand);
                    }
                }

                try {
                    String recv = in.readUTF();
                    if (recv.equalsIgnoreCase("END")) {
                        break;
                    }
                    System.out.println(recv);
                } catch (SocketTimeoutException ignored) {
                } catch (IOException ex) {
                    break;
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
