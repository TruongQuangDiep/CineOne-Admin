package com.example.cineone_admin.exception;
public class AiExtractionException extends RuntimeException {
    public enum Kind {
        INVALID_INPUT, PRIVACY_REJECTED, CONFIGURATION, TIMEOUT,
        RATE_LIMITED, PROVIDER_ERROR, INVALID_RESPONSE, BLOCKED
    }

    private final Kind kind;

    public AiExtractionException(Kind kind) {
        super(switch (kind) {
            case INVALID_INPUT -> "Câu tìm kiếm phải có từ 1 đến 1000 ký tự hợp lệ.";
            case PRIVACY_REJECTED -> "Vui lòng bỏ thông tin cá nhân, địa chỉ riêng và thông tin bí mật khỏi câu tìm kiếm.";
            case CONFIGURATION -> "Dịch vụ bóc tách AI chưa được cấu hình hợp lệ.";
            case TIMEOUT -> "Dịch vụ AI phản hồi quá thời gian cho phép.";
            case RATE_LIMITED -> "Dịch vụ AI đã đạt giới hạn sử dụng. Vui lòng thử lại sau.";
            case PROVIDER_ERROR -> "Không thể kết nối hoặc xử lý yêu cầu với dịch vụ AI.";
            case INVALID_RESPONSE -> "Dịch vụ AI trả về tiêu chí không hợp lệ.";
            case BLOCKED -> "Dịch vụ AI không thể hoàn thành yêu cầu này.";
        });
        this.kind = kind;
    }

    public Kind getKind() { return kind; }
}
