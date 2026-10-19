package com.example.cineone_admin.dto;

import com.example.cineone_admin.dto.auth.LoginRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginRequestValidationTests {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("LoginRequest hợp lệ khi đầy đủ email và password chuẩn")
    void validLoginRequestPasses() {
        LoginRequest request = new LoginRequest("admin@cineone.vn", "password123");
        var violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Báo lỗi validation khi email để trống hoặc sai định dạng")
    void invalidEmailFails() {
        LoginRequest blankEmail = new LoginRequest("", "password123");
        assertFalse(validator.validate(blankEmail).isEmpty());

        LoginRequest malformedEmail = new LoginRequest("not-an-email", "password123");
        assertFalse(validator.validate(malformedEmail).isEmpty());
    }

    @Test
    @DisplayName("Báo lỗi validation khi mật khẩu để trống")
    void blankPasswordFails() {
        LoginRequest blankPassword = new LoginRequest("admin@cineone.vn", "   ");
        var violations = validator.validate(blankPassword);
        assertFalse(violations.isEmpty());
    }
}