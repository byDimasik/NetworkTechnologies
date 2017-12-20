package ru.nsu.fit.g15205.shishlyannikov;

public enum ConnectionState {
    // клиент
    WAIT_REQUEST,
    HEADER_RECEIVED,
    WAIT_BODY,
    WRITE_RESPONSE,
    // сервер
    WRITE_REQUEST,
    WAIT_RESPONSE
}