package com.example.cineone_admin.entity.converter;

import com.example.cineone_admin.model.RoomType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Lưu và đọc nguyên mã loại phòng, không tự quy đổi về STANDARD. */
@Converter
public class RoomTypeConverter implements AttributeConverter<RoomType, String> {
    @Override
    public String convertToDatabaseColumn(RoomType value) {
        return value == null ? null : value.getDatabaseValue();
    }

    @Override
    public RoomType convertToEntityAttribute(String value) {
        return value == null ? null : RoomType.of(value);
    }
}
