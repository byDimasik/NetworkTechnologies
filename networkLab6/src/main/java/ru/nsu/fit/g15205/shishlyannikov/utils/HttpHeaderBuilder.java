package ru.nsu.fit.g15205.shishlyannikov.utils;

public class HttpHeaderBuilder {
    private final String endRow = "\r\n";
    private final String responseTypeOK = "HTTP 200 OK";
    private final String responseTypeNotFound = "HTTP 404 Not Found";
    private final String responseTypeForbidden = "HTTP 403 Forbidden";
    private final String responseTypeUnauthorized = "HTTP 401 Unauthorized";
    private final String responseTypeBadRequest = "HTTP 400 Bad Request";
    private final String responseTypeMethodNotAllowed = "HTTP 405 Method Not Allowed";
    private final String responseTypeInternalServerError = "HTTP 500 Internal Server Error";

    private final String requestTypeLogin = "POST /login HTTP/1.1";


    private final String headerLength = "Content-Length: ";
    private final String headerContent = "Content-Type: application/json";
    private final String headerToken = "Authorization: Token ";
    private final String headerWWWAuthenticate = "WWW-Authenticate: ";

    public String buildResponseOK(int contentLength) {
        return responseTypeOK + endRow +
                headerContent + endRow +
                headerLength + contentLength + endRow + endRow;
    }

    public String buildResponseUnauthorized(String message) {
        return responseTypeUnauthorized + endRow +
                headerLength + "0" + endRow +
                headerWWWAuthenticate + message + endRow + endRow;
    }

    public String buildResponseForbidden() {
        return responseTypeForbidden + endRow +
                headerLength + "0" + endRow + endRow;
    }

    public String buildResponseNotFound() {
        return responseTypeNotFound + endRow +
                headerLength + "0" + endRow + endRow;
    }

    public String buildRequestLogin(int contentLength) {
        return requestTypeLogin + endRow +
               headerContent + endRow +
               headerLength + contentLength + endRow + endRow;
    }
}
