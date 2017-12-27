package ru.nsu.fit.g15205.shishlyannikov;

import ru.nsu.fit.g15205.shishlyannikov.sockets.MySocket;

import java.io.FileInputStream;
import java.io.IOException;

/***
 * Простой пример демонстрации работы. Отправляем файл, ждем сообщения от сервера, завершаем работу
 */
public class ClientApp {
    public static void main( String[] args )
    {
        try {
            MySocket socket = new MySocket("localhost", 1111);

            byte[] hello = new byte[10];
            int num;

            num = socket.receive(hello, 1000);
            if (num > 0) {
                System.out.println(new String(hello, 0 , num));
            }

            FileInputStream inputStream = new FileInputStream("test3.jopa");
            byte[] buffer = new byte[10000];
            int fileSize = 0;
            while ((num = inputStream.read(buffer)) > 0) {
                fileSize+=num;
                byte tmp[] = new byte[num];
                System.arraycopy(buffer, 0, tmp, 0, num);
                socket.send(tmp);
            }
            inputStream.close();
            System.out.println("sent " + socket.count + " bytes");

            socket.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

}
