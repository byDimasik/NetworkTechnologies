package ru.nsu.fit.g15205.shishlyannikov;

public class Node {
    private String type;
    private Connection connection;

    Node (String type, Connection connection) {
        this.type = type;
        this.connection = connection;
    }

    public String getType() {
        return type;
    }

    public Connection getConnection() {
        return connection;
    }
}
