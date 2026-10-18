package com.example.cineone_admin.dto.sync;

import com.example.cineone_admin.enums.ProviderEntityType;
import java.util.Objects;

/** Tham chiếu giữ mã nhà cung cấp để tra cứu đúng ánh xạ định danh. */
public record ProviderReferenceDTO(String providerCode, ProviderEntityType entityType, String externalId) {
    public ProviderReferenceDTO {
        if (providerCode == null || providerCode.isBlank() || providerCode.length() > 50)
            throw new IllegalArgumentException("Mã nhà cung cấp là bắt buộc và không được vượt quá 50 ký tự");
        Objects.requireNonNull(entityType, "Loại thực thể là bắt buộc");
        if (externalId == null || externalId.isBlank() || externalId.length() > 255)
            throw new IllegalArgumentException("ID bên ngoài là bắt buộc và không được vượt quá 255 ký tự");
    }
}
