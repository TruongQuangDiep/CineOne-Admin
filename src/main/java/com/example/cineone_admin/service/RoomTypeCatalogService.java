package com.example.cineone_admin.service;

import com.example.cineone_admin.model.RoomType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Tạo danh mục còn thiếu trong cùng giao dịch lưu phòng; không xác nhận dữ liệu riêng lẻ. */
@Service
public class RoomTypeCatalogService {
    private final EntityManager entityManager;

    public RoomTypeCatalogService(EntityManager entityManager) { this.entityManager = entityManager; }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void registerTypes(Collection<RoomType> types) {
        Objects.requireNonNull(types, "Danh sách loại phòng là bắt buộc");
        var codes = new TreeSet<String>();
        for (var type : types)
            codes.add(Objects.requireNonNull(type, "Loại phòng là bắt buộc").getDatabaseValue());
        var all = new ArrayList<>(codes);
        for (int start = 0; start < all.size(); start += ProviderMappingService.MAX_BATCH_SIZE) {
            var part = all.subList(start, Math.min(start + ProviderMappingService.MAX_BATCH_SIZE, all.size()));
            // Chưa flush phòng đang sửa trước khi danh mục được thêm: khóa ngoại chưa có đích.
            var existing = entityManager.createQuery("select t.code from RoomTypeCatalog t where t.code in :codes", String.class)
                    .setFlushMode(FlushModeType.COMMIT).setParameter("codes", part).getResultList();
            var missing = new ArrayList<>(part);
            missing.removeAll(existing);
            if (missing.isEmpty()) continue;
            var values = new ArrayList<String>();
            for (int i = 0; i < missing.size(); i++) values.add("(:code" + i + ")");
            // MySQL bảo vệ cả cuộc đua khi hai giao dịch cùng phát hiện mã mới.
            var insert = entityManager.createNativeQuery("INSERT INTO roomtypes (code) VALUES "
                    + String.join(",", values) + " ON DUPLICATE KEY UPDATE code = code")
                    .setFlushMode(FlushModeType.COMMIT);
            for (int i = 0; i < missing.size(); i++) insert.setParameter("code" + i, missing.get(i));
            insert.executeUpdate();
        }
    }
}
