package com.example.cineone_admin.dto.booking;

import com.example.cineone_admin.enums.BookingStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateBookingStatusRequest {

    @NotNull(message = "Trạng thái đơn vé không được để trống")
    private BookingStatus status;

    private String note;
}