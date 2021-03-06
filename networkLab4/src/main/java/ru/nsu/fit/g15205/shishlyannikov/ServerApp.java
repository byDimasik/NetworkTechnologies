package ru.nsu.fit.g15205.shishlyannikov;

import ru.nsu.fit.g15205.shishlyannikov.sockets.MyServerSocket;
import ru.nsu.fit.g15205.shishlyannikov.sockets.MySocket;

import java.io.FileOutputStream;
import java.io.IOException;

/***
 * Простой пример демонстрации работы. Принимаем файл, отправляем сообщение, завершаем работу
 */
public class ServerApp {
    public static void main( String[] args )
    {
        try {
            MyServerSocket serverSocket = new MyServerSocket(1111);
            MySocket socket = serverSocket.accept();
            socket.send("Hello!".getBytes());

            FileOutputStream outputStream = new FileOutputStream("recv.jopa");
            byte[] buffer = new byte[20000];
            int fileSize = 0;
            int size;
            while ((size = socket.receive(buffer, 5000)) != -1) {
                outputStream.write(buffer, 0, size);
                fileSize += size;
            }
            outputStream.close();
            System.out.println("received " + fileSize + " bytes");

            socket.close();

            serverSocket.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
