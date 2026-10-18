package com.example.cineone_admin.dto.sync;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Mỗi lần gọi phải trả thực thể mới chưa có ID; hàm có thể được gọi lại sau hoàn tác.
 * Không tự ghi dữ liệu hoặc gọi dịch vụ bên ngoài.
 */
public record EntityCreationPair<T>(String externalId, Supplier<T> newEntity) {
    public EntityCreationPair {
        if (externalId == null || externalId.isBlank() || externalId.length() > 255)
            throw new IllegalArgumentException("ID bên ngoài là bắt buộc và tối đa 255 ký tự");
        Objects.requireNonNull(newEntity, "Hàm tạo thực thể là bắt buộc");
    }
}
