package ru.nsu.fit.g15205.shishlyannikov;

import com.google.gson.Gson;
import ru.nsu.fit.g15205.shishlyannikov.utils.HttpHeaderBuilder;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

public class Client {
    public static void main(String[] args) throws InterruptedException {

        try (Socket socket = new Socket("localhost", 1111);
             BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream()) ) {

            socket.setSoTimeout(500);
            HttpHeaderBuilder headerBuilder = new HttpHeaderBuilder();
            Gson gson = new Gson();

            System.out.print("Введите имя: ");
            String username = br.readLine();

            Map<String, String> loginMap = new HashMap<>();
            loginMap.put("username", username);
            String loginJson = gson.toJson(loginMap, HashMap.class);

            String loginHeader = headerBuilder.buildRequestLogin(loginJson.length());
            String requestLogin = loginHeader + loginJson;


            out.write(requestLogin.getBytes());
            out.flush();

//            while (true) {
//                if (br.ready()) {
//                    clientCommand = br.readLine();
//                    if (clientCommand != null) {
//                        if (clientCommand.equalsIgnoreCase("exit")) {
//                            break;
//                        }
//
//                        out.writeUTF(clientCommand);
//                    }
//                }
//
//                try {
//                    String recv = in.readUTF();
//                    if (recv.equalsIgnoreCase("END")) {
//                        break;
//                    }
//                    System.out.println(recv);
//                } catch (SocketTimeoutException ignored) {
//                } catch (IOException ex) {
//                    break;
//                }
//
//            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
