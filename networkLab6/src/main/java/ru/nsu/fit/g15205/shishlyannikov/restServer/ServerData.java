package ru.nsu.fit.g15205.shishlyannikov.restServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

class ServerData {
    private final Object syncUser = new Object();
    private final Object syncMessage = new Object();
    private final int TIMEOUT = 10000;

    private Map<String, String> clientNames = new ConcurrentHashMap<>();      // token : nickname
    private Map<String, String> clientIDs = new ConcurrentHashMap<>();        // token : uuid
    private Map<String, Long> clientActivity = new ConcurrentHashMap<>();     // token : lastActivity

    private Map<Integer, String> messages = new ConcurrentHashMap<>();        // id    : text
    private Map<Integer, String> messagesAuthors = new ConcurrentHashMap<>(); // id    : authorUUID

    private Map<String, String> timeoutClients = new ConcurrentHashMap<>();
    private Map<String, String> logoutClients = new ConcurrentHashMap<>();

    private int messageCounter = 0;
    private Thread timeoutChecker;

    ServerData() {
        timeoutChecker = new Thread(() -> {
           while (!Thread.currentThread().isInterrupted()) {
               try {
                   Thread.sleep(TIMEOUT);
               } catch (InterruptedException ex) {
                   break;
               }

               long now = System.currentTimeMillis();

               synchronized (syncUser) {
                   for (Map.Entry<String, Long> entry : clientActivity.entrySet()) {
                       if (now - entry.getValue() > TIMEOUT) {
                           String token = entry.getKey();
                           System.err.println("TIMEOUT remove: " + clientNames.get(entry.getKey()));
                           timeoutClients.put(clientIDs.get(token), clientNames.get(token));

                           clientActivity.remove(entry.getKey());
                           clientNames.remove(entry.getKey());
                           clientIDs.remove(entry.getKey());
                       }
                   }
               }

           }
        });
        timeoutChecker.start();
    }

    void close() {
        timeoutChecker.interrupt();
    }

    boolean containsToken(String token) {
        synchronized (syncUser) {
            return clientNames.containsKey(token);
        }
    }

    boolean containsNickname(String nickname) {
        synchronized (syncUser) {
            return clientNames.containsValue(nickname);
        }
    }

    Map<String, String> loginClient(String nickname) {
        Map<String, String> clientInfo = null;

        synchronized (syncUser) {
            if (!clientNames.containsValue(nickname)) {
                String token = UUID.randomUUID().toString();
                token = token.replaceAll("-", "");
                clientNames.put(token, nickname);

                String uuid  = UUID.randomUUID().toString();
                clientIDs.put(token, uuid);
                clientActivity.put(token, System.currentTimeMillis());

                clientInfo = new HashMap<>();
                clientInfo.put("token", token);
                clientInfo.put("uuid", uuid);
            }
        }

        return clientInfo;
    }

    void logoutClient(String token) {
        synchronized (syncUser) {
            logoutClients.put(clientIDs.get(token), clientNames.get(token));

            clientActivity.remove(token);
            clientIDs.remove(token);
            clientNames.remove(token);
        }
    }

    int addMessage(String token, String text) {
        synchronized (syncUser) {
            clientActivity.put(token, System.currentTimeMillis());
        }
        synchronized (syncMessage) {
            messages.put(++messageCounter, text);
            messagesAuthors.put(messageCounter, clientIDs.get(token));
            return messageCounter;
        }
    }

    ArrayList<Map<String, String>> getMessages(String token, int offset, int count) {
        synchronized (syncUser) {
            clientActivity.put(token, System.currentTimeMillis());
        }

        ArrayList<Map<String, String>> result = new ArrayList<>();
        synchronized (syncMessage) {
            int countIndex = offset + count + 1;
            int size = countIndex > messages.size() + 1 ? messages.size() + 1 : countIndex;
            for (int i = offset + 1; i < size; i++) {
                Map<String, String> messageInfo = new HashMap<>();
                messageInfo.put("id", String.valueOf(i));
                messageInfo.put("message", messages.get(i));
                messageInfo.put("author", messagesAuthors.get(i));

                result.add(messageInfo);
            }

            return result;
        }
    }

    ArrayList<Map<String, String>> getActiveUsers(String token) {
        ArrayList<Map<String, String>> users = new ArrayList<>();
        synchronized (syncUser) {
            clientActivity.put(token, System.currentTimeMillis());

            for (Map.Entry<String, String> client : clientNames.entrySet()) {
                Map<String, String> clientInfo = new HashMap<>();
                clientInfo.put("id", clientIDs.get(client.getKey()));
                clientInfo.put("username", client.getValue());
                clientInfo.put("online", "true");

                users.add(clientInfo);
            }
        }

        return users;
    }

}
