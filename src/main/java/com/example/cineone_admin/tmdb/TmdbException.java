package com.example.cineone_admin.tmdb;

/** Không đưa token, request headers hoặc response body của nhà cung cấp vào thông báo lỗi. */
public class TmdbException extends RuntimeException {
    public enum Kind { CONFIGURATION, TIMEOUT, HTTP, NETWORK, INVALID_RESPONSE }
    private final Kind kind;
    private final Integer statusCode;

    public TmdbException(Kind kind, String message) { this(kind, message, null); }
    public TmdbException(Kind kind, String message, Integer statusCode) {
        super(message);
        this.kind = kind;
        this.statusCode = statusCode;
    }
    public Kind getKind() { return kind; }
    public Integer getStatusCode() { return statusCode; }
}
