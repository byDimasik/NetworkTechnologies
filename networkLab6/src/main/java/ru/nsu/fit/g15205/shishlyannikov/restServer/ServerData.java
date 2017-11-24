package ru.nsu.fit.g15205.shishlyannikov.restServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ServerData {
    private final Object sync = new Object();

    private Map<String, String> clientNames = new HashMap<>(); // token : nickname
    private Map<String, String> clientIDs = new HashMap<>();   // token : uuid
    private ArrayList<String> messages = new ArrayList<>();

    boolean containsToken(String token) {
        synchronized (sync) {
            return clientNames.containsKey(token);
        }
    }

    boolean containsNickname(String nickname) {
        synchronized (sync) {
            return clientNames.containsValue(nickname);
        }
    }

    Map<String, String> addClient(String nickname) {
        Map<String, String> clientInfo = null;

        synchronized (sync) {
            if (!clientNames.containsValue(nickname)) {
                String token = UUID.randomUUID().toString();
                token = token.replaceAll("-", "");
                clientNames.put(token, nickname);

                String uuid  = UUID.randomUUID().toString();
                clientIDs.put(token, uuid);

                clientInfo = new HashMap<>();
                clientInfo.put("token", token);
                clientInfo.put("uuid", uuid);
            }
        }

        return clientInfo;
    }

    void removeClient(String token) {
        synchronized (sync) {
            clientNames.remove(token);
            clientIDs.remove(token);
        }
    }

}
