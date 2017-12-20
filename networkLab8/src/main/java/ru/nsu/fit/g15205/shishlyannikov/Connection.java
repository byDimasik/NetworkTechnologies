package ru.nsu.fit.g15205.shishlyannikov;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;

public class Connection {
    private static int idCounter = 1;

    private int serverSocketNumber;
    private ConnectionState state = ConnectionState.WAIT_REQUEST;

    private ByteBuffer headerBuffer = null;
    private ByteBuffer bodyBuffer = null;
    private ByteBuffer responseBuffer = null;

    private byte[] endHeader;

    private String headerString;
    private int contentLength = -1;

    private HeaderParser headerParser;

    Connection() {
        serverSocketNumber = idCounter++;

        try {
            endHeader = "\r\n\r\n".getBytes("UTF-8");
        } catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();
        }

    }

    public int getServerSocketNumber() {
        return serverSocketNumber;
    }

    public void setHeaderReceived() {
        state = ConnectionState.HEADER_RECEIVED;
        try {
            headerString = new String(headerBuffer.array(), "UTF-8");
            headerParser = new HeaderParser(headerString);
            if (bodyBuffer != null) {
                contentLength = getContentLength();
                contentLength -= bodyBuffer.array().length;
            }
        } catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();
        }
    }

    public ConnectionState getState() {
        return state;
    }

    public void setState(ConnectionState state) {
        this.state = state;
    }

    public String getNewHeader() {
        return headerParser.makeHeader();
    }

    public String getMethod() {
        return headerParser.getMethod();
    }

    public String getHost() {
        return headerParser.getHost();
    }

    public int getPort() {
        return headerParser.getPort();
    }

    public int getContentLength() {
        return contentLength;
    }

    public String getProtocol() {
        return headerParser.getProtocol();
    }

    public void addToHeader(ByteBuffer buffer) {
        buffer.flip();

        if (null == headerBuffer) headerBuffer = buffer;
        else {
            ByteBuffer newBuffer = ByteBuffer.allocate(headerBuffer.capacity() + buffer.capacity());
            newBuffer.put(headerBuffer);
            newBuffer.put(buffer);
            newBuffer.flip();
            headerBuffer = newBuffer;
        }

        byte[] headerBytes = headerBuffer.array();
        int count = 0;
        int endHeaderIndex = -1;
        for (int i = 0; i < headerBytes.length - endHeader.length + 1; i++) {
            if (endHeaderIndex != -1) break;

            for (int j = 0; j < endHeader.length; j++) {
                if (headerBytes[i + j] == endHeader[j]) {
                    count++;
                    if (count == endHeader.length) {
                        endHeaderIndex = i + j;
                        break;
                    }
                } else {
                    count = 0;
                    break;
                }
            }
        }
        if (-1 != endHeaderIndex) {
            ByteBuffer newBuffer = ByteBuffer.allocate(endHeaderIndex + 1);
            newBuffer.put(Arrays.copyOf(headerBuffer.array(), endHeaderIndex + 1));
            newBuffer.flip();

            if (endHeaderIndex != headerBuffer.array().length - 1) {
                bodyBuffer = ByteBuffer.wrap(headerBuffer.array(), endHeaderIndex + 1, headerBuffer.array().length - endHeaderIndex - 1);
            }
            headerBuffer = newBuffer;
            setHeaderReceived();
        }

        //System.err.println(new String(headerBuffer.array()));
    }

    public void addToBody(ByteBuffer buffer) {
        reallocByteBuffer(buffer, null == bodyBuffer);

        contentLength -= buffer.array().length;
    }

    public void addToResponse(ByteBuffer buffer) {
        buffer.flip();

        if (null == responseBuffer) {
            responseBuffer = buffer;
        } else {
            ByteBuffer newBuffer = ByteBuffer.allocate(responseBuffer.capacity() + buffer.capacity());
            newBuffer.put(responseBuffer);
            newBuffer.put(buffer);
            newBuffer.flip();
            responseBuffer = newBuffer;
        }
    }

    public ByteBuffer getResponse() {
        return responseBuffer;
    }

    public ByteBuffer getBody() {
        return bodyBuffer;
    }

    private void reallocByteBuffer(ByteBuffer buffer, boolean b) {
        buffer.flip();

        if (b) {
            bodyBuffer = buffer;
        } else {
            ByteBuffer newBuffer = ByteBuffer.allocate(bodyBuffer.capacity() + buffer.capacity());
            newBuffer.put(bodyBuffer);
            newBuffer.put(buffer);
            newBuffer.flip();
            bodyBuffer = newBuffer;
        }
    }
}
