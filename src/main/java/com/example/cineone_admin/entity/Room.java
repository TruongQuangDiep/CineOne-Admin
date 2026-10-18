package com.example.cineone_admin.entity;

import jakarta.persistence.*;
import com.example.cineone_admin.model.RoomType;
import com.example.cineone_admin.entity.converter.RoomTypeConverter;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "rooms")
@Getter
@Setter
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Long roomId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;

    @Column(nullable = false, length = 100)
    private String name;

    @Convert(converter = RoomTypeConverter.class)
    @Column(name = "room_type", nullable = false, length = RoomType.MAX_LENGTH)
    private RoomType roomType = RoomType.STANDARD;

    /** Quan hệ đọc danh mục; cột room_type được ghi bằng mã qua RoomTypeConverter. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_type", referencedColumnName = "code", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_rooms_room_type"))
    private RoomTypeCatalog roomTypeCatalog;

    @Column(nullable = false, length = 20, columnDefinition = "ENUM('ACTIVE','INACTIVE','MAINTENANCE')")
    private String status = "ACTIVE";
}