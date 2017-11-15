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
            FileInputStream inputStream = new FileInputStream("test.jpg");
            MySocket socket = new MySocket("localhost", 1111);

            byte[] buffer = new byte[10000];
            int fileSize = 0;
            int num;
            while ((num = inputStream.read(buffer)) > 0) {
                fileSize+=num;
                byte tmp[] = new byte[num];
                for (int i = 0; i < num; i++) {
                    tmp[i] = buffer[i];
                }
                socket.send(tmp);
            }
            inputStream.close();
            System.out.println("sent " + fileSize + " bytes");

            // тк сервер из receive выйдет только через 2 секунды после нашего последнего send, нужно дать ему время
            // успеть отправить сообщение нам, потому что наш receive тоже через 2 секунды вырубится, и если сервер
            // не успеет отправить, мы не сможем ничего принять, для этого засыпаем на 5 секунд (чтоб с запасом)
            // несмотря на большой sleep лично у меня на компьютере при тестах сервер иногда не успевал все принять
            // но это нормально. Изящным решением будет добавить возможность устанавливать timeout в receive, но этого
            // нет в ТЗ, и вообще к лабе не относится, а уже красявости
            Thread.sleep(5000);
            num = socket.receive(buffer);
            if (num > 0) {
                System.out.println("SERVER: " + new String(buffer).substring(0, num));
            }
            socket.close();
        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace();
        }

    }

}
