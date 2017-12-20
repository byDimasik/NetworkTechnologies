package ru.nsu.fit.g15205.shishlyannikov;

import java.io.IOException;

public class App {
    public static void main( String[] args ) {
        switch (args[0]) {
            case "s":
                try {
                    new Server();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case "c":
                String str = "http://192.168.1.172:1111/";
                //String str = "http://172.16.17.82:1111/";
                new Client(str);

        }
    }
}
