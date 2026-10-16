package com.example.cineone_admin.dto.room;

import com.example.cineone_admin.enums.RoomStatus;
import com.example.cineone_admin.enums.RoomType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Shared create/full-replacement payload. Update ID belongs in the URL. */
@Getter
@Setter
public class RoomRequest {
    @NotNull
    @Positive
    private Long cinemaId;

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    private RoomType roomType;

    @NotNull
    private RoomStatus status;
}
