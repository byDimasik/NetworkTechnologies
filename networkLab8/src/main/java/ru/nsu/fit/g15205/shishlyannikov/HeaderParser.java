package ru.nsu.fit.g15205.shishlyannikov;

import java.net.MalformedURLException;
import java.net.URL;

public class HeaderParser {
    private String header;

    private String protocol = null;
    private String method = null;
    private String host;
    private int port;
    private int contentLength = -1;

    private String path;
    private String newFirstRow;
    private String[] headerRows;

    HeaderParser(String header) {
        this.header = header;

        parseHeader();
    }

    public String getMethod() {
        return method;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getContentLength() {
        return contentLength;
    }

    public String getProtocol() {
        return protocol;
    }

    private void parseHeader() {
        String[] rows = header.split("\r\n");
        headerRows = rows;

        String[] firstRow = rows[0].split(" ");

        method = firstRow[0];

        try {
            URL url = new URL(firstRow[1]);

            protocol = url.getProtocol();
            host = url.getHost();
            port = url.getPort();

            path = url.getPath();
            if (url.getQuery() != null) {
                path += "?" + url.getQuery();
            }

            newFirstRow = method + " " + path + " " + firstRow[2];
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        }

        for (String row : rows) {
            if (row.startsWith("Content-Length: ") || row.startsWith("Content-length: ")) {
                String[] tmp = row.split(": ");
                contentLength = Integer.valueOf(tmp[1]);
                break;
            }
        }
    }

    public String makeHeader() {
        boolean connectionClose = false;

        String request = newFirstRow + "\r\n";
        for (int i = 1; i < headerRows.length; i++) {
            if (headerRows[i].startsWith("Connection: ")) {
                request += "Connection: close" + "\r\n";
                connectionClose = true;
                continue;
            }
            request += headerRows[i] + "\r\n";
        }

        if (!connectionClose) {
            request += "Connection: close" + "\r\n";
        }

        request += "\r\n";

        return request;
    }


}
