package ru.nsu.fit.g15205.shishlyannikov.restServer;

import com.google.gson.Gson;
import ru.nsu.fit.g15205.shishlyannikov.utils.HttpHeaderBuilder;
import ru.nsu.fit.g15205.shishlyannikov.utils.HttpHeaderParser;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
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

                    Map<String, String> arguments = new HashMap<>();
                    String tmp = checkArguments(requestType, arguments);

                    if (tmp != null) {
                        requestType = tmp;
                    } else {
                        arguments = null;
                    }

                    printRequest(stringRequest, requestType, requestHeader, jsonMap);

                    String token;
                    switch (requestType) {
                        case "POST /login":
                            login(jsonMap);
                            break;
                        case "GET /logout":
                            token = checkToken(requestHeader);
                            if (token == null) {
                                break;
                            }
                            logout(token);
                            break;
                        case "POST /messages":
                            token = checkToken(requestHeader);
                            if (token == null) {
                                break;
                            }
                            addMessage(token, jsonMap);
                            break;
                        case "GET /messages":
                            token = checkToken(requestHeader);
                            if (token == null) {
                                break;
                            }
                            sendMessages(token, arguments);
                            break;
                        case "GET /users":
                            token = checkToken(requestHeader);
                            if (token == null) {
                                break;
                            }
                            sendActiveUsersInfo(token);
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

    private String checkArguments(String requestType, Map<String, String> arguments) {
        if (!requestType.contains("?")) {
            return null;
        }

        String[] typeAndArguments = requestType.split("\\?");
        String newRequestType = typeAndArguments[0];
        String[] argumentsForParse = typeAndArguments[1].split("&");

        for (String s : argumentsForParse) {
            String[] parseArguments = s.split("=");
            arguments.put(parseArguments[0], parseArguments[1]);
        }

        return newRequestType;
    }

    private String checkToken(Map<String, String> header) {
        String token = null;
        try {
            if (!header.containsKey("Authorization")) {
                String response = headerBuilder.buildResponseUnauthorized("Token realm='Need token'");
                out.write(response.getBytes());
                out.flush();

                return null;
            }

            token = header.get("Authorization").split(" ")[1];

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

        if (jsonMap != null) {
            System.out.println("____________________BODY____________________");
            for (Map.Entry<String, String> entry : jsonMap.entrySet()) {
                System.out.println(entry.getKey() + " : " + entry.getValue());
            }
            System.out.println("____________________________________________\n");
        }

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
            Map<String, String> clientInfo = serverData.loginClient(jsonMap.get("username"));

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
        serverData.logoutClient(token);

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

    private void addMessage(String token, Map<String, String> jsonMap) {
        int id = serverData.addMessage(token, jsonMap.get("message"));

        jsonMap.put("id", String.valueOf(id));
        String json = gson.toJson(jsonMap, HashMap.class);

        String responseHeader = headerBuilder.buildResponseOK(json.length());
        String response = responseHeader + json;

        try {
            out.write(response.getBytes());
            out.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void sendMessages(String token, Map<String, String> arguments) {
        int offset;
        int count;

        if (arguments == null) {
            offset = 10;
            count = 10;
        } else {
            offset = Integer.valueOf(arguments.get("offset"));
            count = Integer.valueOf(arguments.get("count"));
            if (count > 100) {
                count = 100;
            }
        }

        ArrayList<Map<String, String>> messages = serverData.getMessages(token, offset, count);
        Map<String, ArrayList<Map<String, String>>> responseMap = new HashMap<>();

        responseMap.put("messages", messages);
        String responseJson = gson.toJson(responseMap, HashMap.class);

        String responseHeader = headerBuilder.buildResponseOK(responseJson.length());

        String response = responseHeader + responseJson;
        try {
            out.write(response.getBytes());
            out.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void sendActiveUsersInfo(String token) {
        ArrayList<Map<String, String>> users = serverData.getActiveUsers(token);

        Map<String, ArrayList<Map<String, String>>> responseMap = new HashMap<>();
        responseMap.put("users", users);

        String responseJson = gson.toJson(responseMap, HashMap.class);
        String responseHeader = headerBuilder.buildResponseOK(responseJson.length());
        String response = responseHeader + responseJson;

        try {
            out.write(response.getBytes());
            out.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
