package ru.nsu.fit.g15205.shishlyannikov.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class HttpHeaderParser {
    public Map<String, String> parseHTTPHeaders(String toParse) throws IOException {
        int charRead;
        InputStream inputStream = new ByteArrayInputStream(toParse.getBytes());
        StringBuilder sb = new StringBuilder();

        while (true) {
            charRead = inputStream.read();
            sb.append((char) charRead);

            if ((char) charRead == '\r') {                // if we've got a '\r'
                sb.append((char) inputStream.read());     // then write '\n'

                charRead = inputStream.read();            // read the next char;
                if ((char) charRead == '\r') {            // if it's another '\r'
                    sb.append((char) inputStream.read()); // write the '\n'
                    break;
                } else {
                    sb.append((char) charRead);
                }
            }
        }

        String[] headersArray = sb.toString().split("\r\n");

        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < headersArray.length - 1; i++) {
            headers.put(headersArray[i].split(": ")[0],
                    headersArray[i].split(": ")[1]);
        }

        return headers;
    }

    public String getHeaderType(String stringRequest) {
        // Чтоб узнать тип запроса, обрезаем первую строку, потом обрезаем версию HTTP/1.1
        String requestType = stringRequest.substring(0, stringRequest.indexOf('\r'));
        requestType = requestType.substring(0, requestType.lastIndexOf(' '));

        return requestType;

    }

    public void printHttpMessage(String stringResponse, String responseType, Map<String, String> responseHeader, Map<String, String> jsonMap) {
        System.out.println(stringResponse);

        System.out.println("____________________TYPE____________________");
        System.out.println(responseType);
        System.out.println("____________________________________________\n");

        System.out.println("___________________HEADER___________________");
        for (Map.Entry<String, String> entry : responseHeader.entrySet()) {
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
}
