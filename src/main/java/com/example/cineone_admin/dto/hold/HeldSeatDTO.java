package com.example.cineone_admin.dto.hold;

import com.google.cloud.Timestamp;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeldSeatDTO {
    private Long seatId;
    private Long showtimeId;
    private String seatCode;
    private Long userId;
    private String status; // HOLDING hoặc BOOKED
    private String holdExpiresAt;
    private String createdAt;
}