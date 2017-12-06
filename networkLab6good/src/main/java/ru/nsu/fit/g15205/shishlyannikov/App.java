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
                String str = "http://localhost:1111/";
                try {
                    new Client(str);
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
    }
}
