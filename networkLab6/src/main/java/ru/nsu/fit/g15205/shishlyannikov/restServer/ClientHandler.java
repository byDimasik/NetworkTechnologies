package ru.nsu.fit.g15205.shishlyannikov.restServer;

import com.google.gson.Gson;
import ru.nsu.fit.g15205.shishlyannikov.utils.HttpHeaderBuilder;
import ru.nsu.fit.g15205.shishlyannikov.utils.HttpHeaderParser;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

public class ClientHandler implements Runnable {

    private Socket clientSocket;
    private ServerData serverData;
    private DataOutputStream out;

    private HttpHeaderParser headerParser = new HttpHeaderParser();
    private HttpHeaderBuilder headerBuilder = new HttpHeaderBuilder();
    private Gson gson = new Gson();

    ClientHandler(Socket client, ServerData data) {
        clientSocket = client;
        serverData = data;
        try {
            clientSocket.setSoTimeout(300);
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            out = new DataOutputStream(clientSocket.getOutputStream());
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            while (!clientSocket.isClosed() && !Thread.currentThread().isInterrupted()) {
                try {
                    // Получаем http запрос
                    int symbol;
                    StringBuilder stringBuilder = new StringBuilder();
                    while (in.ready()) {
                        symbol = in.read();
                        stringBuilder.append((char) symbol);
                    }
                    if (stringBuilder.toString().equals("")) {
                        continue;
                    }
                    // --------------------

                    String stringRequest = stringBuilder.toString();
                    String requestType = getRequestType(stringRequest);
                    Map<String, String> requestHeader = headerParser.parseHTTPHeaders(stringRequest);
                    String requestBody = stringRequest.substring(stringRequest.indexOf("\r\n\r\n") + 4);
                    HashMap<String, String> jsonMap = gson.fromJson(requestBody, HashMap.class);

                    printRequest(stringRequest, requestType, requestHeader, jsonMap);



                    switch (requestType) {
                        case "POST /login":
                            login(jsonMap);
                            break;
                        case "POST /logout":
                            String token = checkToken(requestHeader);
                            if (token == null) {
                                break;
                            }

                            logout(token);
                            break;
                    }
                } catch (SocketTimeoutException ignored) {
                } catch (IOException ex) {
                    break;
                }
            }

            in.close();
            out.close();

            // потом закрываем сокет общения с клиентом в нити моносервера
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String checkToken(Map<String, String> header) {
        String token = null;
        try {
            if (!header.containsKey("authorization")) {
                String response = headerBuilder.buildResponseUnauthorized("Token realm='Need token'");
                out.write(response.getBytes());
                out.flush();

                return null;
            }

            token = header.get("authorization").split(" ")[1];

            if (!serverData.containsToken(token)) {
                String response = headerBuilder.buildResponseForbidden();
                out.write(response.getBytes());
                out.flush();

                return null;
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return token;
    }

    private void printRequest(String stringRequest, String requestType, Map<String, String> requestHeader, Map<String, String> jsonMap) {
        System.out.println(stringRequest);

        System.out.println("____________________TYPE____________________");
        System.out.println(requestType);
        System.out.println("____________________________________________\n");

        System.out.println("___________________HEADER___________________");
        for (Map.Entry<String, String> entry : requestHeader.entrySet()) {
            System.out.println(entry.getKey() + " : " + entry.getValue());
        }
        System.out.println("____________________________________________\n");

        System.out.println("____________________BODY____________________");
        for (Map.Entry<String, String> entry : jsonMap.entrySet()) {
            System.out.println(entry.getKey() + " : " + entry.getValue());
        }
        System.out.println("____________________________________________\n");

    }

    private String getRequestType(String stringRequest) {
        // Чтоб узнать тип запроса, обрезаем первую строку, потом обрезаем версию HTTP/1.1
        String requestType = stringRequest.substring(0, stringRequest.indexOf('\r'));
        requestType = requestType.substring(0, requestType.lastIndexOf(' '));

        return requestType;

    }

    private void login(Map<String, String> jsonMap) {
        try {
            // пытаемся добавить клиента в нашу БД
            Map<String, String> clientInfo = serverData.addClient(jsonMap.get("username"));

            // если не получилось - HTTP 401
            if (clientInfo == null) {
                String responseHeader = headerBuilder.buildResponseUnauthorized("Token realm='Username is already in use'");
                out.write(responseHeader.getBytes());
                out.flush();
                System.out.println("__________________RESPONSE__________________");
                System.out.println(responseHeader);
                System.out.println("____________________________________________\n");
            }
            // если получилось - HTTP 200 OK
            else {
                HashMap<String, String> responseMap = new HashMap<>();
                responseMap.put("id", clientInfo.get("uuid"));
                responseMap.put("username", jsonMap.get("username"));
                responseMap.put("online", "true");
                responseMap.put("token", clientInfo.get("token"));
                String responseJson = gson.toJson(responseMap, HashMap.class);

                String responseHeader = headerBuilder.buildResponseOK(responseJson.length());

                String response = responseHeader + responseJson;
                out.write(response.getBytes());
                out.flush();
                System.out.println("__________________RESPONSE__________________");
                System.out.println(response);
                System.out.println("____________________________________________\n");
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void logout(String token) {
        serverData.removeClient(token);

        HashMap<String, String> responseMap = new HashMap<>();
        responseMap.put("message", "bye!");
        String responseBody = gson.toJson(responseMap, HashMap.class);

        String responseHeader = headerBuilder.buildResponseOK(responseBody.length());

        String response = responseHeader + responseBody;

        try {
            out.write(response.getBytes());
            out.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
