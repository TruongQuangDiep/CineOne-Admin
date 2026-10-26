package com.example.cineone_admin.dto.hold;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatHoldResponse {
    private Long showtimeId;
    private Long userId;
    private List<Long> heldSeatIds;
    private List<String> heldSeatCodes;
    private Instant expiresAt;
    private long remainingSeconds;
    private String message;
}