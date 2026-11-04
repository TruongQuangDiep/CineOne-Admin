package com.example.cineone_admin.dto.catalog;

import com.example.cineone_admin.entity.Seat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatResponse {
    private Long seatId;
    private Long roomId;
    private String seatRow;
    private Integer seatNumber;
    private String seatType;   // "NORMAL", "VIP", "SWEETBOX"
    private String status;     // "ACTIVE", "INACTIVE", "MAINTENANCE"

    public static SeatResponse from(Seat seat) {
        return SeatResponse.builder()
                .seatId(seat.getSeatId())
                .roomId(seat.getRoom() != null ? seat.getRoom().getRoomId() : null)
                .seatRow(seat.getSeatRow())
                .seatNumber(seat.getSeatNumber())
                .seatType(seat.getSeatType() != null ? seat.getSeatType().name() : "NORMAL")
                .status(seat.getStatus() != null ? seat.getStatus().name() : "ACTIVE")
                .build();
    }
}