package ru.nsu.fit.g15205.shishlyannikov;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import com.google.gson.Gson;

class Client {
    private String url;
    private String username;
    private String token;
    private int id;
    private int offset = 0;
    private Scanner scanner;

    private ConcurrentHashMap<Integer, String> onlineUsers = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, String> usersStatus = new ConcurrentHashMap<>(); // username : status
    private BlockingQueue<String> queue = new LinkedBlockingDeque<>();

    private Thread user;
    private Thread getter;
    private Thread printer;

    Client(String url) throws IOException {
        this.url = url;
        tryToLogin();
        System.out.println("Print \"/logout\" to exit");

        user = new Thread(new User());
        getter = new Thread(new Getter());
        printer = new Thread(new Printer());

        user.start();
        getter.start();
        printer.start();
    }

    private class User implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()){
                try {
                    String string = scanner.nextLine();
                    switch (string) {
                        case "/logout":
                            logout();
                            user.interrupt();
                            getter.interrupt();
                            printer.interrupt();
                            System.exit(0);
                            break;
                        case "/list":
                            System.out.println(onlineUsers.values());
                            break;
                        default:
                            messages(string);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class Getter implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()){
                try {
                    int quantity = messages(offset, 100);
                    if (quantity != -1) {
                        offset += quantity;
                    }

                    for (Map.Entry<String,String> entry : usersStatus.entrySet()){
                        if (!entry.getKey().equals(username)) {
                            switch (entry.getValue()) {
                                case "online":
                                    System.out.println(entry.getKey() + " now online.");
                                    break;
                                case "offline":
                                    System.out.println(entry.getKey() + " has gone offline.");
                                    break;
                                case "timeout":
                                    System.out.println(entry.getKey() + " was disconnected by timeout.");
                                    break;
                            }
                        }
                        usersStatus.remove(entry.getKey());
                    }

                    users();

                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    break;
                } catch (IOException ex) {
                    System.err.println("Сервер помер");
                    System.exit(0);
                }
            }
        }
    }

    private class Printer implements Runnable{
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String msg = queue.take();
                    if (msg != null) System.out.println(msg);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private int login(String name) throws IOException {
        URL urlLogin = new URL(url + "login");
        HttpURLConnection urlConnection = connection(urlLogin,"POST", "JSON");
        HashMap<String,String> req = new HashMap<>();
        req.put("username", name);
        Gson gson = new Gson();
        String request = gson.toJson(req);

        try (OutputStream out = urlConnection.getOutputStream()) {
            out.write(request.getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        int respCode = urlConnection.getResponseCode();

        switch (respCode) {
            case 200:
                byte[] bytes = urlConnection.getInputStream().readAllBytes();
                String resp = new String(bytes, "UTF-8");
                HashMap<String,String> json = gson.fromJson(resp, HashMap.class);
                username = json.get("username");
                token = json.get("token");
                id = Integer.valueOf(json.get("id"));
                break;
            case 401:
                System.out.println("This username is already in use");
                break;
        }

        return respCode;
    }

    private boolean tryToLogin(){
        String name;
        System.out.print("Enter your username: ");
        scanner = new Scanner(System.in, "UTF-8");
        try {
            name = scanner.nextLine();
            int code;
            while ((code = login(name)) == 401){
                System.out.print("Enter your username: ");
                name = scanner.nextLine();
            }

            if (code == 200) {
                username = name;
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private int logout() throws IOException {
        URL urlLogout = new URL(url + "logout");
        HttpURLConnection urlConnection = connection(urlLogout,"GET","TOKEN");
        return urlConnection.getResponseCode();
    }

    private int users() throws IOException {
        URL urlUsers = new URL(url + "users");
        HttpURLConnection urlConnection = connection(urlUsers,"GET","TOKEN");
        int responseCode = urlConnection.getResponseCode();
        if (responseCode == 200) {
            String resp = new String(urlConnection.getInputStream().readAllBytes(), "UTF-8");
            Gson gson = new Gson();
            HashMap<String, ArrayList<HashMap<String, String>>> hashMap = gson.fromJson(resp, HashMap.class);
            ArrayList<HashMap<String, String>> list = hashMap.get("users");

            HashMap<Integer,String> fresh = new HashMap<>();
            for (Map<String,String> map : list) {
                fresh.put(Integer.valueOf(map.get("id")), map.get("username"));
                if (!onlineUsers.containsKey(Integer.valueOf(map.get("id")))){
                    usersStatus.put(map.get("username"), "online");
                }
            }

            for (Map.Entry<Integer,String> entry : onlineUsers.entrySet()) {
                if (!fresh.containsKey(entry.getKey())) {
                    users(entry.getKey());
                }
            }

            onlineUsers.putAll(fresh);
        }
        return responseCode;
    }

    private String users(int userID) throws IOException {
        String name = null;
        URL urlUsers = new URL(url + "users/" + userID);
        HttpURLConnection urlConnection = connection(urlUsers,"GET","TOKEN");
        int responseCode = urlConnection.getResponseCode();
        if (responseCode == 200) {
            String resp = new String(urlConnection.getInputStream().readAllBytes(), "UTF-8");
            Gson gson = new Gson();
            HashMap<String, String> hashMap = gson.fromJson(resp, HashMap.class);
            name = hashMap.get("username");
            if ((!"true".equals(hashMap.get("online"))) && onlineUsers.containsKey(userID)) {
                onlineUsers.remove(userID);
                if ("false".equals(hashMap.get("online"))) {
                    usersStatus.put(name, "offline");
                } else {
                    usersStatus.put(name, "timeout");
                }
            }
        }

        return name;
    }

    // отправить сообщение
    private int messages(String msg) throws IOException {
        URL urlMessages= new URL(url + "messages");
        HttpURLConnection urlConnection = connection(urlMessages,"POST","JT");
        HashMap<String,String> req = new HashMap<>();
        req.put("message",msg);
        Gson gson = new Gson();
        String request = gson.toJson(req);
        try (OutputStream out = urlConnection.getOutputStream()){
            out.write(request.getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return urlConnection.getResponseCode();
    }

    // получить сообщения
    private int messages(int... arguments) throws IOException {
        URL urlMessages = new URL(url + "messages");
        HttpURLConnection urlConnection;
        if (arguments.length == 2) {
            urlConnection = connection(urlMessages, "GET", "TOKEN", arguments[0], Math.min(arguments[1],100));
        } else {
            urlConnection = connection(urlMessages, "GET", "TOKEN", 0, 10);
        }

        int responseCode = urlConnection.getResponseCode();
        int quantity = -1;

        if (responseCode == 200) {
            String resp = new String(urlConnection.getInputStream().readAllBytes(), "UTF-8");
            Gson gson = new Gson();
            HashMap<String, ArrayList<HashMap<String, String>>> hashMap = gson.fromJson(resp, HashMap.class);
            ArrayList<HashMap<String, String>> list = hashMap.get("messages");
            if (list.size() == 0) {
                return quantity;
            }
            quantity = list.size();
            for (Map<String,String> map : list) {
                int author = Integer.valueOf(map.get("author"));
                if (author != id){
                    try {
                        queue.put(users(author) + ": " + map.get("message"));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return quantity;
    }

    private HttpURLConnection connection(URL urlc, String method, String prop, int... arguments) throws IOException {
        if (arguments.length == 2) {
            urlc = new URL(urlc.toString() + "?" + "offset=" + arguments[0] + "&" + "count=" + arguments[1]);
        }
        HttpURLConnection urlConnection = (HttpURLConnection) urlc.openConnection();
        urlConnection.setRequestMethod(method);
        switch (prop) {
            case "TOKEN":
                urlConnection.setRequestProperty("Authorization", "Token " + token);
                break;
            case "JSON":
                urlConnection.setRequestProperty("Content-Type", "application/json");
                break;
            case "JT":
                urlConnection.setRequestProperty("Authorization", "Token " + token);
                urlConnection.setRequestProperty("Content-Type", "application/json");
                break;
        }

        urlConnection.setDoOutput(true);
        return urlConnection;

    }
}
