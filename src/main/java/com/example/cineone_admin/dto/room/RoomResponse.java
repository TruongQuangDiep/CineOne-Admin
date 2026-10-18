package com.example.cineone_admin.dto.room;

import com.example.cineone_admin.enums.RoomStatus;
import com.example.cineone_admin.model.RoomType;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoomResponse {
    private Long roomId;
    private Long cinemaId;
    private String cinemaName;
    private String name;
    private RoomType roomType;
    private RoomStatus status;
    private Long seatCount;
    /** Read-only seat map for room detail; not part of room writes. */
    private List<SeatSummary> seats;

    public record SeatSummary(Long seatId, String seatRow, Integer seatNumber,
                              String seatType, String status) {}

}
