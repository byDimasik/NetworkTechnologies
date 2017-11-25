package ru.nsu.fit.g15205.shishlyannikov.restServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Server extends Thread {

    @Override
    public void run() {
        ArrayList<Thread> clients = new ArrayList<>();
        int clientNum = 1;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            ServerSocket server = new ServerSocket(1111);
            server.setSoTimeout(500);

            ServerData serverData = new ServerData();

            // стартуем цикл при условии что серверный сокет не закрыт
            while (!server.isClosed()) {

                // проверяем поступившие комманды из консоли сервера если такие
                // были
                if (br.ready()) {
                    // если команда - quit то инициализируем закрытие сервера и
                    // выход из цикла раздачии нитей монопоточных серверов
                    String serverCommand = br.readLine();
                    if (serverCommand.equalsIgnoreCase("exit")) {

                        break;
                    }
                }

                // если комманд от сервера нет то становимся в ожидание
                // подключения к сокету общения под именем - "clientDialog" на
                // серверной стороне
                try {
                    Socket client = server.accept();
                    Thread newClient = new Thread(new ClientHandler(client, serverData), "Client" + clientNum);
                    newClient.start();
                    clients.add(newClient);
                } catch (SocketTimeoutException ignore) {}
            }

            for (Thread client : clients) {
                client.interrupt();
            }
            serverData.close();

            server.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
