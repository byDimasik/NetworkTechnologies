package ru.nsu.fit.g15205.shishlyannikov;

import ru.nsu.fit.g15205.shishlyannikov.restClient.Client;

import java.io.IOException;

public class runClient {
    public static void main(String[] args) throws IOException {
        Thread client = new Client();
        client.start();
    }
}
