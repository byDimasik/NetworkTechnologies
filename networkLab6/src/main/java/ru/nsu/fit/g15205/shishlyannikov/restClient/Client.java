package ru.nsu.fit.g15205.shishlyannikov.restClient;

import com.google.gson.Gson;
import ru.nsu.fit.g15205.shishlyannikov.utils.HttpHeaderBuilder;
import ru.nsu.fit.g15205.shishlyannikov.utils.HttpPacketReceiver;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Client extends Thread {
    private final int TIMEOUT = 1000;

    private String token;
    private String myUUID;
    private Gson gson = new Gson();
    private HttpHeaderBuilder headerBuilder = new HttpHeaderBuilder();
    private HttpPacketReceiver receiver;

    private Socket socket;
    private DataOutputStream out;
    private BufferedReader in;

    private Thread messageReceiver;

    public Client() throws IOException {
        socket = new Socket("localhost", 1111);
        socket.setSoTimeout(500);

        messageReceiver = new Thread(() -> {
            int offset = 0;
            int count = 10;

            while (!currentThread().isInterrupted()) {
                try {
                    ArrayList<Map<String, String>> messages = getMessages(offset, count);

                    if (messages.size() != 0) {
                        for (Map<String, String> message : messages) {
                            if (myUUID.equals(message.get("author"))) {
                                continue;
                            }
                            System.out.println(getUserName(message.get("author")) + ": " + message.get("message"));

                        }

                        offset += messages.size();
                    }
                } catch (IOException ex) {
                    endWork();
                    break;
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    break;
                }
            }
        });
    }

    private void endWork() {
        try {
            System.in.close();

            socket.close();
            out.close();
            in.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void run() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {

            out = new DataOutputStream(socket.getOutputStream());
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            receiver = new HttpPacketReceiver(in);

            System.out.print("Введите имя: ");
            String username = br.readLine();
            System.out.println("Print \"/exit\" to exit");

            try {
                login(username);
            } catch (IOException ex) {
                System.err.println("Сервер недоступен.");
                return;
            }

            messageReceiver.start();

            String message;
            while (true) {
                try {
                    message = br.readLine();
                } catch (IOException ex) {
                    break;
                }
                if (message != null) {
                    if (message.equalsIgnoreCase("/exit")) {
                        logout();
                        break;
                    }
                    if ("".equals(message)) {
                        continue;
                    }

                    try {
                        if ("/list".equals(message)) {
                            getUsers();
                            continue;
                        }

                        sendMessage(message);
                    } catch (IOException ex) {
                        break;
                    }
                }

            }

            messageReceiver.interrupt();
            try {
                messageReceiver.join();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }

            endWork();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /***
     * Отправляет пакет, возвращает ответ
     */
    synchronized private String sendPacket(String packet) throws IOException {
        out.write(packet.getBytes());
        out.flush();

        // ждем пока сервер ответит
        long time = System.currentTimeMillis();
        while (!in.ready()) {
            if (System.currentTimeMillis() - time > TIMEOUT) {
                System.err.println("Сервер помер.");
                throw new IOException("Response receive timeout");
            }
            continue;
        }

        String response = receiver.receivePacket();
        if (response == null) {
            System.err.println("Сервер помер");
            throw new IOException("Response receive error");
        }

        return response;
    }

    private void login(String username) throws IOException {
        Map<String, String> loginMap = new HashMap<>();
        loginMap.put("username", username);
        String loginJson = gson.toJson(loginMap, HashMap.class);

        String loginHeader = headerBuilder.buildRequestLogin(loginJson.length());
        String requestLogin = loginHeader + loginJson;

        String response = sendPacket(requestLogin);

        String responseBody = response.substring(response.indexOf("\r\n\r\n") + 4);
        HashMap<String, String> jsonMap = gson.fromJson(responseBody, HashMap.class);

        token = jsonMap.get("token");
        myUUID = jsonMap.get("id");
    }

    private void logout() throws IOException {
        String request = headerBuilder.buildRequestLogout(token);

        sendPacket(request);
    }

    private void sendMessage(String message) throws IOException {
        Map<String, String> requestMap = new HashMap<>();
        requestMap.put("message", message);

        String requestJson = gson.toJson(requestMap);
        String requestHeader = headerBuilder.buildRequestSendMessage(token, requestJson.length());
        String request = requestHeader + requestJson;

        sendPacket(request);
    }

    private void getUsers() throws IOException {
        String request = headerBuilder.buildRequestGetUsers(token);

        String response = sendPacket(request);
        String responseBody = response.substring(response.indexOf("\r\n\r\n") + 4);
        HashMap<String, ArrayList<Map<String, String>>> jsonMap = gson.fromJson(responseBody, HashMap.class);

        String userList = "Online users:\n";

        for (Map<String, String> userInfo : jsonMap.get("users")) {
            userList += "\t" + userInfo.get("username") + "\n";
        }

        System.out.println(userList);
    }

    private ArrayList<Map<String, String>> getMessages(int offset, int count) throws IOException {
        String request = headerBuilder.buildRequestGetMessages(token, offset, count);

        String response = sendPacket(request);
        String responseBody = response.substring(response.indexOf("\r\n\r\n") + 4);
        HashMap<String, ArrayList<Map<String, String>>> jsonMap = gson.fromJson(responseBody, HashMap.class);

        return jsonMap.get("messages");

    }

    private String getUserName(String uuid) throws IOException {
        String request = headerBuilder.buildRequestGetUser(token, uuid);

        String response = sendPacket(request);

        String responseBody = response.substring(response.indexOf("\r\n\r\n") + 4);
        Map<String, String> userInfo = gson.fromJson(responseBody, HashMap.class);

        return userInfo.get("username");
    }
}
