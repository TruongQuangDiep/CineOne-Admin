package com.example.cineone_admin.dto.sync;

import java.util.List;
import java.util.Objects;

/** Số lượng thể hiện kết quả lấy và chuẩn hóa dữ liệu, chưa phản ánh số bản ghi đã lưu thành công. */
public record SyncResultDTO(String providerCode, NormalizedCatalogDTO data,
        int successCount, int failureCount, List<SyncErrorDTO> errors) {
    public SyncResultDTO {
        if (providerCode == null || providerCode.isBlank() || providerCode.length() > 50)
            throw new IllegalArgumentException("Mã nhà cung cấp là bắt buộc và không được vượt quá 50 ký tự");
        Objects.requireNonNull(data, "Dữ liệu chuẩn hóa là bắt buộc");
        errors = List.copyOf(errors);
        if (successCount != data.size() || failureCount != errors.size())
            throw new IllegalArgumentException("Số lượng phải khớp với số bản ghi chuẩn hóa và số lỗi");
    }

    public static SyncResultDTO of(String providerCode, NormalizedCatalogDTO data, List<SyncErrorDTO> errors) {
        return new SyncResultDTO(providerCode, data, data.size(), errors.size(), errors);
    }

    public boolean isSuccessful() { return failureCount == 0; }
}
