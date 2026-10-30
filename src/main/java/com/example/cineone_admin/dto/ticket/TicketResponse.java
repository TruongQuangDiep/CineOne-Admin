package com.example.cineone_admin.dto.ticket;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {
    private Long ticketId;
    private String ticketCode;
    private String bookingCode;
    private String movieTitle;
    private String moviePoster;
    private String cinemaName;
    private String roomName;
    private String seats;
    private String seatCodes;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime showtime;

    private String showDate;
    private String showTime;
    private String showDateTime;
    private BigDecimal totalAmount;
    private String status;
    private String qrCode;
    private String qrCheckInCode;
}