package com.example.cineone_admin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import tools.jackson.databind.JsonNode;

/** Mã loại phòng mở: giữ nguyên giá trị nguồn, không cần bổ sung enum khi xuất hiện mã mới. */
public record RoomType(String value) {
    public static final int MAX_LENGTH = 50;
    public static final RoomType STANDARD = new RoomType("STANDARD");
    public static final RoomType IMAX = new RoomType("IMAX");
    public static final RoomType FOUR_DX = new RoomType("4DX");
    public static final RoomType SCREENX = new RoomType("SCREENX");

    public RoomType {
        if (value == null || value.isBlank() || value.length() > MAX_LENGTH
                || !value.equals(value.strip()) || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Loại phòng phải là chuỗi từ 1 đến 50 ký tự, không có ký tự điều khiển hoặc khoảng trắng ở hai đầu");
    }

    public static RoomType of(String value) { return new RoomType(value); }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static RoomType fromJson(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (!value.isString()) throw new IllegalArgumentException("Loại phòng phải là chuỗi");
        return of(value.stringValue());
    }

    @JsonValue
    public String getDatabaseValue() { return value; }
}
