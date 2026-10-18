package com.example.cineone_admin.dto.sync;

import com.example.cineone_admin.enums.ProviderEntityType;
import com.example.cineone_admin.enums.SyncMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/** Phạm vi phải được truyền rõ ràng; Adapter báo lỗi nếu API không hỗ trợ yêu cầu. */
public record SyncRequestDTO(Set<ProviderEntityType> entityTypes, SyncMode mode,
        LocalDate fromDate, LocalDate toDate, Instant updatedSince) {
    public SyncRequestDTO {
        entityTypes = Set.copyOf(Objects.requireNonNull(entityTypes, "Phạm vi đồng bộ là bắt buộc"));
        if (entityTypes.isEmpty()) throw new IllegalArgumentException("Phải chọn ít nhất một loại thực thể");
        Objects.requireNonNull(mode, "Chế độ đồng bộ là bắt buộc");
        if ((fromDate == null) != (toDate == null))
            throw new IllegalArgumentException("Phải cung cấp cả ngày bắt đầu và ngày kết thúc");
        if (fromDate != null && (toDate.isBefore(fromDate) || !entityTypes.contains(ProviderEntityType.SHOWTIME)))
            throw new IllegalArgumentException("Khoảng ngày phải hợp lệ và chỉ dùng khi có đồng bộ suất chiếu");
        if (mode == SyncMode.INCREMENTAL && updatedSince == null)
            throw new IllegalArgumentException("Đồng bộ tăng dần cần mốc thời gian cập nhật");
        if (mode == SyncMode.FULL && updatedSince != null)
            throw new IllegalArgumentException("Đồng bộ toàn bộ không nhận mốc thời gian cập nhật");
    }
}
