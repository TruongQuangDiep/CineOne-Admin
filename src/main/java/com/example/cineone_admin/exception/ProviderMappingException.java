package com.example.cineone_admin.exception;

/** Báo lỗi khi ánh xạ xung đột, không hoạt động hoặc trỏ tới dữ liệu không còn tồn tại. */
public class ProviderMappingException extends RuntimeException {
    public ProviderMappingException(String message) { super(message); }
}
