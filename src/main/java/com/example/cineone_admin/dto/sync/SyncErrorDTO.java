package com.example.cineone_admin.dto.sync;

import com.example.cineone_admin.enums.ProviderEntityType;

/** externalId có thể null khi nhà cung cấp không gửi ID; entityType có thể null khi lỗi xảy ra ở cấp nhà cung cấp. */
public record SyncErrorDTO(ProviderEntityType entityType, String externalId, String message) {
    public SyncErrorDTO {
        if (message == null || message.isBlank()) throw new IllegalArgumentException("Nội dung thông báo lỗi là bắt buộc");
    }
}
