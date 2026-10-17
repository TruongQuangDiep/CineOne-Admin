package com.example.cineone_admin.dto.booking;

import com.example.cineone_admin.enums.BookingStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BookingResponse {
    private Long bookingId;
    private String bookingCode;

    // Khách hàng đặt vé
    private Long userId;
    private String customerName;
    private String customerPhone;

    // Thông tin suất chiếu
    private Long showtimeId;
    private String movieTitle;
    private String cinemaName;
    private String roomName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime showtimeStart;

    // Chi tiết tài chính
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private BookingStatus status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime holdExpiresAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}