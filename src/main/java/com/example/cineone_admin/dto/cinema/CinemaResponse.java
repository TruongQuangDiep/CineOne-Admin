package com.example.cineone_admin.dto.cinema;

import com.example.cineone_admin.dto.room.RoomResponse;
import com.example.cineone_admin.enums.CinemaStatus;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CinemaResponse {
    private Long cinemaId;
    private Long brandId;
    private String brandName;
    private String brandLogoUrl;
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String phone;
    private CinemaStatus status;
    private Long roomCount;
    /** Populated for detail responses; may be null in list responses. */
    private List<RoomResponse> rooms;
}
