package ru.nsu.fit.g15205.shishlyannikov.restClient;

import com.google.gson.Gson;
import ru.nsu.fit.g15205.shishlyannikov.utils.HttpHeaderBuilder;
import ru.nsu.fit.g15205.shishlyannikov.utils.HttpHeaderParser;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Client extends Thread {
    private String token;
    private String myUUID;
    private Gson gson = new Gson();
    private HttpHeaderBuilder headerBuilder = new HttpHeaderBuilder();
    private HttpHeaderParser headerParser = new HttpHeaderParser();

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
                ArrayList<Map<String, String>> messages = getMessages(offset, count);

                if (messages.size() != 0) {
                    // TODO сделать сообщения в правильном порядке. Начинать выводить с id == offset + 1 и так до
                    // TODO messages.size()
                    for (Map<String, String> message : messages) {
                        if (myUUID.equals(message.get("author"))) {
                            continue;
                        }
                        System.out.println(getUserName(message.get("author")) + ": " + message.get("message"));
                    }

                    offset += messages.size();
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    break;
                }
            }
        });
    }

    @Override
    public void run() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {

            out = new DataOutputStream(socket.getOutputStream());
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));


            System.out.print("Введите имя: ");
            String username = br.readLine();
            System.out.println("Print \"/exit\" to exit");

            login(username);
            messageReceiver.start();

            String message;
            while (true) {
                message = br.readLine();
                if (message != null) {
                    if (message.equalsIgnoreCase("/exit")) {
                        logout();
                        break;
                    }
                    if ("".equals(message)) {
                        continue;
                    }

                    sendMessage(message);
                }

            }

            messageReceiver.interrupt();
            try {
                messageReceiver.join();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }

            socket.close();
            out.close();
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /***
     * Отправляет пакет, возвращает ответ
     */
    synchronized private String sendPacket(String packet) {
        try {
            out.write(packet.getBytes());
            out.flush();

            int symbol;
            StringBuilder stringBuilder = new StringBuilder();

            // ждем пока сервер ответит
            while (!in.ready()) {
                continue;
            }

            while (in.ready()) {
                symbol = in.read();
                stringBuilder.append((char) symbol);
            }

            return stringBuilder.toString();
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return null;
    }

    private void login(String username) {
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

    private void logout() {
        String request = headerBuilder.buildRequestLogout(token);

        sendPacket(request);
    }

    private void sendMessage(String message) {
        Map<String, String> requestMap = new HashMap<>();
        requestMap.put("message", message);

        String requestJson = gson.toJson(requestMap);
        String requestHeader = headerBuilder.buildRequestSendMessage(token, requestJson.length());
        String request = requestHeader + requestJson;

        String response = sendPacket(request);

    }

    private ArrayList<Map<String, String>> getMessages(int offset, int count) {
        String request = headerBuilder.buildRequestGetMessages(token, offset, count);

        String response = sendPacket(request);

        String responseBody = response.substring(response.indexOf("\r\n\r\n") + 4);
        HashMap<String, ArrayList<Map<String, String>>> jsonMap = gson.fromJson(responseBody, HashMap.class);

        return jsonMap.get("messages");
    }

    private String getUserName(String uuid) {
        String request = headerBuilder.buildRequestGetUser(token, uuid);

        String response = sendPacket(request);

        String responseBody = response.substring(response.indexOf("\r\n\r\n") + 4);
        Map<String, String> userInfo = gson.fromJson(responseBody, HashMap.class);

        return userInfo.get("username");
    }
}
