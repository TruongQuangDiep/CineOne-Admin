package com.example.cineone_admin.entity;

import com.example.cineone_admin.model.RoomType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Danh mục loại phòng dùng chung; mã nguồn là khóa, không cần sửa enum khi có loại mới. */
@Entity
@Table(name = "roomtypes")
@Getter
@NoArgsConstructor
public class RoomTypeCatalog {
    @Id
    @Column(name = "code", nullable = false, length = RoomType.MAX_LENGTH)
    private String code;

    public RoomTypeCatalog(RoomType type) {
        this.code = java.util.Objects.requireNonNull(type, "Loại phòng là bắt buộc").getDatabaseValue();
    }
}
