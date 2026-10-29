package com.example.cineone_admin.service;

import com.example.cineone_admin.exception.AiExtractionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SearchInputGuardTests {
    private final SearchInputGuard guard = new SearchInputGuard();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "phim\u0000ma"})
    void rejectsInvalidInput(String input) {
        assertEquals(AiExtractionException.Kind.INVALID_INPUT,
                assertThrows(AiExtractionException.class, () -> guard.validate(input)).getKind());
    }

    @Test void limitsLengthBeforeExternalCall() {
        assertThrows(AiExtractionException.class, () -> guard.validate("a".repeat(1001)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "tìm phim cho abc@example.com", "abc @ example.com muốn xem phim",
            "gọi 0901234567 rồi tìm phim", "+84 901 234 567", "090.123.4567",
            "mật khẩu: sample-secret", "OTP 123456", "Bearer synthetic-token",
            "email tui là abc", "tui tên Nguyễn Văn A", "CCCD 123456789012",
            "số tài khoản 123456", "số thẻ 123456", "CVV 123",
            "nhà tôi ở 12 Nguyễn Trãi", "tui ở 12 Nguyễn Trãi", "tôi sống tại Thủ Đức", "gần 12 đường Nguyễn Trãi",
            "gần tọa độ 10.123456,106.123456", "API_KEY=sensitive",
            "eyJhbGciOiJIUzI1NiJ9.synthetic.signature"
    })
    void blocksKnownPrivateData(String input) {
        var error = assertThrows(AiExtractionException.class, () -> guard.validate(input));
        assertEquals(AiExtractionException.Kind.PRIVACY_REJECTED, error.getKind());
        assertFalse(error.getMessage().contains(input));
        assertNull(error.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "phim kinh dị ngày mai gần tôi", "phim gần nhà",
            "CGV Thủ Đức", "phim ngày 30/10/2026 lúc 20 giờ",
            "phim 2026-10-30 20 giờ", "phim ngày 30-10-2026 20 giờ",
            "phim từ 22 giờ tới 1 giờ sáng hôm sau"
    })
    void preservesSearchDetailsAndSymbolicLocation(String input) {
        assertEquals(input, guard.validate(input));
    }
}
