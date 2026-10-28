package com.example.cineone_admin.dto.hold;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SeatHoldRequest {
    @NotNull(message = "showtimeId không được để trống")
    private Long showtimeId;

    @NotEmpty(message = "Danh sách seatIds không được để trống")
    private List<Long> seatIds;

    private Long userId;
}