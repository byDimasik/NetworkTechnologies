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
}
