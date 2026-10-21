package com.example.cineone_admin.sync;

import com.example.cineone_admin.dto.sync.SyncResultDTO;
import com.example.cineone_admin.dto.sync.SyncRequestDTO;

/** Mỗi nhà cung cấp có một Spring bean riêng để gọi API và chuẩn hóa dữ liệu trả về. */
public interface CinemaSyncAdapter {
    String providerCode();

    /** Chỉ lấy và chuẩn hóa dữ liệu. Lỗi ở cấp nhà cung cấp được biểu diễn bằng SyncErrorDTO. */
    SyncResultDTO sync(SyncRequestDTO request);

    /** A run fixes the Vietnam reference date before reading any source files. */
    default SyncResultDTO sync(SyncRequestDTO request, java.time.LocalDate referenceDate) {
        return sync(request);
    }
}
