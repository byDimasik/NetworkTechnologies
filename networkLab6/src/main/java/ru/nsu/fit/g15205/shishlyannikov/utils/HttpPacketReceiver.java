package ru.nsu.fit.g15205.shishlyannikov.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

public class HttpPacketReceiver {
    private BufferedReader in;
    private HttpHeaderParser parser = new HttpHeaderParser();

    public HttpPacketReceiver(BufferedReader bufferedReader) {
        in = bufferedReader;
    }

    public String receiveHeader() {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            while (!in.ready() && !Thread.currentThread().isInterrupted()) {
                continue;
            }

            while (true) {
                int symbol = in.read();

                if (symbol == -1) {
                    return null;
                }

                stringBuilder.append((char) symbol);

                if (stringBuilder.toString().contains("\r\n\r\n")) {
                    break;
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return stringBuilder.toString();
    }

    public String receiveBody(Map<String, String> header) throws IOException {
        int bodySize = Integer.valueOf(header.get("Content-Length"));

        StringBuilder stringBuilder = new StringBuilder();

        try {
            while (!in.ready() && !Thread.currentThread().isInterrupted()) {
                continue;
            }

            int count = 0;
            while (count < bodySize) {
                int symbol = in.read();

                if (symbol == -1) {
                    return null;
                }

                stringBuilder.append((char) symbol);
                count++;
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return stringBuilder.toString();
    }

    public String receivePacket() {
        String header = receiveHeader();

        try {
            Map<String, String> headerMap = parser.parseHTTPHeaders(header);
            String body = receiveBody(headerMap);
            return header + body;
        } catch (IOException ex) {
            return null;
        }
    }
}
