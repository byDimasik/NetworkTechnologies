package ru.nsu.fit.g15205.shishlyannikov;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.Gson;

class Server {

    private HttpServer httpServer;
    private ConcurrentHashMap<String, String> userNames = new ConcurrentHashMap<>();     //token = username
    private ConcurrentHashMap<String, Integer> usersIds = new ConcurrentHashMap<>();     //token = userId
    private ConcurrentHashMap<String, Long> usersActivity = new ConcurrentHashMap<>();   //token = time

    private ConcurrentHashMap<Integer, String> logoutUsers = new ConcurrentHashMap<>();  //userId = username
    private ConcurrentHashMap<Integer, String> timeOutUsers = new ConcurrentHashMap<>(); //userId = username

    private ConcurrentHashMap<Integer, String> messages = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, Integer> senders = new ConcurrentHashMap<>();

    private int userId = 0;
    private int messageId = 0;
    private long timeout = 10000;

    private class Cleaner implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    long time = System.currentTimeMillis();
                    for (Map.Entry<String, Long> entry : usersActivity.entrySet()) {
                        if (time - entry.getValue() > timeout) {
                            String token = entry.getKey();
                            timeOutUsers.put(usersIds.remove(token), userNames.remove(token));
                            usersActivity.remove(token);
                        }
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    break;
                }
            }
        }
    }

    Server() throws IOException {
        httpServer = HttpServer.create();
        httpServer.bind(new InetSocketAddress(1111),0);
        httpServer.createContext("/login", new LoginHandler());
        httpServer.createContext("/logout", new LogoutHandler());
        httpServer.createContext("/users", new UsersHandler());
        httpServer.createContext("/messages", new MessagesHandler());

        Thread cleaner = new Thread(new Cleaner());
        cleaner.start();

        httpServer.setExecutor(null);
        httpServer.start();

        System.out.println("Print \"exit\" to exit");
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String input = scanner.nextLine();
            if ("exit".equals(input)) {
                cleaner.interrupt();
                System.exit(0);
            }
        }
    }

    class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                Gson gson = new Gson();
                HashMap<String, String> hashMap = gson.fromJson(body, HashMap.class);
                String name = hashMap.get("username");

                if ((name == null) || (hashMap.size() != 1)) {
                    exchange.sendResponseHeaders(400, 0);
                    exchange.close();
                } else if (userNames.containsValue(name)) {
                    Headers headers = exchange.getRequestHeaders();
                    ArrayList<String> list = new ArrayList<>();
                    list.add("Token realm='Username is already in use'");
                    headers.put("WWW-Authenticate", list);

                    exchange.sendResponseHeaders(401, 0);
                    exchange.close();
                } else {
                    String token = UUID.randomUUID().toString().replaceAll("-", "");
                    userNames.put(token, name);
                    usersIds.put(token, userId++);
                    usersActivity.put(token, System.currentTimeMillis());

                    HashMap<String, String> response = new HashMap<>();
                    response.put("id", String.valueOf(userId - 1));
                    response.put("username", name);
                    response.put("online", "true");
                    response.put("token", token);
                    String resp = gson.toJson(response, HashMap.class);

                    exchange.sendResponseHeaders(200, resp.getBytes("UTF-8").length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(resp.getBytes("UTF-8"));
                    }
                    exchange.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                Headers headers = exchange.getRequestHeaders();
                String token = getToken(headers);

                if (userNames.containsKey(token)) {
                    logoutUsers.put(usersIds.remove(token), userNames.remove(token));
                    usersActivity.remove(token);

                    ArrayList<String> list = new ArrayList<>();
                    list.add("application/json");
                    headers.put("Content-Type", list);
                    HashMap<String, String> response = new HashMap<>();
                    response.put("message", "bye!");
                    Gson gson = new Gson();
                    String resp = gson.toJson(response);

                    exchange.sendResponseHeaders(200, resp.getBytes("UTF-8").length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(resp.getBytes("UTF-8"));
                    }
                    exchange.close();
                } else {
                    exchange.sendResponseHeaders(403, 0);
                    exchange.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    class UsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                Headers headers = exchange.getRequestHeaders();
                String token = getToken(headers);

                if (userNames.containsKey(token)) {
                    usersActivity.put(token, System.currentTimeMillis());
                    String path = exchange.getRequestURI().getPath();

                    if (path.equals("/users")) {
                        HashMap<String, ArrayList<HashMap<String, String>>> response = new HashMap<>();
                        ArrayList<HashMap<String, String>> list = new ArrayList<>();

                        for (Map.Entry<String, String> entry : userNames.entrySet()) {
                            String t = entry.getKey();
                            HashMap<String, String> hashMap = new HashMap<>();
                            hashMap.put("id", String.valueOf(usersIds.get(t)));
                            hashMap.put("username", userNames.get(t));
                            hashMap.put("online", "true");
                            list.add(hashMap);
                        }
                        response.put("users", list);

                        ArrayList<String> l = new ArrayList<>();
                        l.add("application/json");
                        headers.put("Content-Type", l);
                        Gson gson = new Gson();
                        String resp = gson.toJson(response);

                        exchange.sendResponseHeaders(200, resp.getBytes("UTF-8").length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(resp.getBytes("UTF-8"));
                        }
                        exchange.close();
                    } else if (path.startsWith("/users/")) {
                        try {
                            int id = Integer.valueOf(path.substring("/users/".length()));

                            if (usersIds.containsValue(id) || (logoutUsers.containsKey(id) || timeOutUsers.containsKey(id))) {
                                ArrayList<String> l = new ArrayList<>();
                                l.add("application/json");
                                headers.put("Content-Type", l);
                                HashMap<String, String> hashMap = new HashMap<>();
                                hashMap.put("id", String.valueOf(id));

                                if (logoutUsers.containsKey(id) || timeOutUsers.containsKey(id)) {
                                    hashMap.put("username", logoutUsers.containsKey(id) ? logoutUsers.get(id) : timeOutUsers.get(id));
                                    hashMap.put("online", logoutUsers.containsKey(id) ? "false" : "null");
                                } else {
                                    // ищем токен по айди
                                    String t = "";
                                    for (Map.Entry<String, Integer> entry : usersIds.entrySet()) {
                                        if (entry.getValue().equals(id)) {
                                            t = entry.getKey();
                                            break;
                                        }
                                    }
                                    hashMap.put("username", userNames.get(t));
                                    hashMap.put("online", "true");
                                }
                                Gson gson = new Gson();
                                String resp = gson.toJson(hashMap);

                                exchange.sendResponseHeaders(200, resp.getBytes("UTF-8").length);
                                try (OutputStream os = exchange.getResponseBody()) {
                                    os.write(resp.getBytes("UTF-8"));
                                }
                                exchange.close();
                            } else {
                                exchange.sendResponseHeaders(404, 0);
                                exchange.close();
                            }
                        } catch (NumberFormatException e) {
                            exchange.sendResponseHeaders(500, 0);
                            exchange.close();
                        }

                    } else {
                        exchange.sendResponseHeaders(400, 0);
                        exchange.close();
                    }
                } else {
                    exchange.sendResponseHeaders(403, 0);
                    exchange.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    class MessagesHandler implements HttpHandler{
        @Override
        public void handle(HttpExchange exchange) {
            try {
                Headers headers = exchange.getRequestHeaders();
                String token = getToken(headers);

                if (userNames.containsKey(token)) {
                    usersActivity.put(token, System.currentTimeMillis());
                    String method = exchange.getRequestMethod();

                    switch (method) {
                        case "POST":
                            String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                            Gson gson = new Gson();
                            HashMap<String, String> hashMap = gson.fromJson(body, HashMap.class);
                            String message = hashMap.get("message");

                            if ((message == null) || (hashMap.size() != 1)) {
                                exchange.sendResponseHeaders(400, 0);
                                exchange.close();
                            } else {
                                senders.put(messageId, usersIds.get(token));
                                messages.put(messageId++, message);
                                HashMap<String, String> response = new HashMap<>();
                                response.put("id", String.valueOf(usersIds.get(token)));
                                response.put("message", message);
                                String resp = gson.toJson(response);

                                exchange.sendResponseHeaders(200, resp.getBytes("UTF-8").length);
                                try (OutputStream os = exchange.getResponseBody()) {
                                    os.write(resp.getBytes("UTF-8"));
                                }
                                exchange.close();
                            }
                            break;
                        case "GET":
                            try {
                                String arguments = exchange.getRequestURI().getQuery();
                                Map<String, Integer> result = new HashMap<>();
                                for (String param : arguments.split("&")) {
                                    String pair[] = param.split("=");
                                    result.put(pair[0], Integer.valueOf(pair[1]));
                                }
                                int offset = result.get("offset");
                                int count = result.get("count");

                                HashMap<String, ArrayList<HashMap<String, String>>> response = new HashMap<>();
                                ArrayList<HashMap<String, String>> list = new ArrayList<>();
                                int i = 0;
                                for (Map.Entry<Integer, String> entry : messages.entrySet()) {
                                    if (i++ < offset) continue;
                                    if (i - offset - 1 > count) break;

                                    int mid = entry.getKey();
                                    HashMap<String, String> map = new HashMap<>();
                                    map.put("id", String.valueOf(mid));
                                    map.put("message", entry.getValue());
                                    map.put("author", String.valueOf(senders.get(mid)));
                                    list.add(map);
                                }
                                response.put("messages", list);
                                ArrayList<String> l = new ArrayList<>();
                                l.add("application/json");
                                headers.put("Content-Type", l);
                                gson = new Gson();
                                String resp = gson.toJson(response);

                                exchange.sendResponseHeaders(200, resp.getBytes("UTF-8").length);
                                try (OutputStream os = exchange.getResponseBody()) {
                                    os.write(resp.getBytes("UTF-8"));
                                }
                                exchange.close();
                            } catch (NumberFormatException e) {
                                exchange.sendResponseHeaders(500, 0);
                                exchange.close();
                            }
                            break;
                        default:
                            exchange.sendResponseHeaders(400, 0);
                            exchange.close();
                    }
                } else {
                    exchange.sendResponseHeaders(403, 0);
                    exchange.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private String getToken(Headers headers) {
        String token = headers.get("Authorization").get(0);
        return token.substring(token.indexOf(" ")+1);
    }
}

