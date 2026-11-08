package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.booking.BookingResponse;
import com.example.cineone_admin.dto.booking.UpdateBookingStatusRequest;
import com.example.cineone_admin.entity.Booking;
import com.example.cineone_admin.entity.BookingSeat;
import com.example.cineone_admin.entity.Seat;
import com.example.cineone_admin.enums.BookingStatus;
import com.example.cineone_admin.exception.ResourceNotFoundException;
import com.example.cineone_admin.repository.BookingRepository;
import com.example.cineone_admin.repository.BookingSeatRepository;
import com.example.cineone_admin.repository.SeatRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.cineone_admin.entity.Showtime;
import com.example.cineone_admin.entity.User;
import com.example.cineone_admin.exception.BusinessException;
import com.example.cineone_admin.repository.ShowtimeRepository;
import com.example.cineone_admin.repository.UserRepository;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BookingService {

        
    private final BookingRepository bookingRepository;
    private final ShowtimeRepository showtimeRepository;
    private final UserRepository userRepository;
    private final SeatRepository seatRepository;             
    private final BookingSeatRepository bookingSeatRepository;

    @Transactional(readOnly = true)
    public Page<BookingResponse> getBookings(String keyword, String status, Pageable pageable) {
        BookingStatus parsedStatus = parseStatus(status);
        return bookingRepository.searchBookings(keyword, parsedStatus, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Booking getBookingEntity(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt vé ID: " + id));
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long id) {
        return mapToResponse(getBookingEntity(id));
    }

    @Transactional
    public void updateBookingStatus(Long id, UpdateBookingStatusRequest request) {
        Booking booking = getBookingEntity(id);
        if (request.getStatus() != null) {
            booking.setStatus(request.getStatus());
        }
        bookingRepository.save(booking);
    }

    public BookingResponse mapToResponse(Booking booking) {
        BookingResponse res = new BookingResponse();
        res.setBookingId(booking.getBookingId());
        res.setBookingCode(booking.getBookingCode());
        res.setSubtotal(booking.getSubtotal());
        res.setDiscountAmount(booking.getDiscountAmount());
        res.setTotalAmount(booking.getTotalAmount());
        res.setHoldExpiresAt(booking.getHoldExpiresAt());
        res.setCreatedAt(booking.getCreatedAt());

        if (booking.getStatus() != null) {
            res.setStatus(booking.getStatus());
        }

        if (booking.getUser() != null) {
            res.setUserId(booking.getUser().getUserId());
            res.setCustomerName(booking.getUser().getFullName());
            res.setCustomerPhone(booking.getUser().getPhone());
        }

        if (booking.getShowtime() != null) {
            res.setShowtimeId(booking.getShowtime().getShowtimeId());
            res.setShowtimeStart(booking.getShowtime().getStartTime());
            if (booking.getShowtime().getMovie() != null) {
                res.setMovieTitle(booking.getShowtime().getMovie().getTitle());
            }
            if (booking.getShowtime().getRoom() != null) {
                res.setRoomName(booking.getShowtime().getRoom().getName());
                if (booking.getShowtime().getRoom().getCinema() != null) {
                    res.setCinemaName(booking.getShowtime().getRoom().getCinema().getName());
                }
            }
        }
        return res;
    }

    private BookingStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }

        try {
            return BookingStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Transactional
    public Map<String, Object> createBookingFromMobile(Map<String, Object> request, Authentication authentication) {
        // 1. Xác định người dùng đặt vé (nếu đăng nhập lấy user thật, nếu là khách lấy user mặc định trong DB)
        User user = null;
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            String principal = authentication.getName();
            user = userRepository.findByEmail(principal)
                    .or(() -> userRepository.findByPhone(principal))
                    .or(() -> {
                        try {
                            return userRepository.findById(Long.parseLong(principal));
                        } catch (Exception e) {
                            return java.util.Optional.empty();
                        }
                    })
                    .orElse(null);
        }
        if (user == null) {
            user = userRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new BusinessException("Chưa có thông tin người dùng trong hệ thống."));
        }

        // 2. Lấy suất chiếu
        Number showtimeIdNum = (Number) request.get("showtimeId");
        if (showtimeIdNum == null) {
            throw new BusinessException("Vui lòng cung cấp mã suất chiếu (showtimeId).");
        }
        Long showtimeId = showtimeIdNum.longValue();
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy suất chiếu ID: " + showtimeId));

        // 3. Tính toán số tiền
        Number subtotalNum = (Number) request.getOrDefault("subtotal", 0);
        Number discountNum = (Number) request.getOrDefault("discountAmount", 0);
        Number totalAmountNum = (Number) request.getOrDefault("totalAmount", 0);

        BigDecimal subtotal = subtotalNum != null ? BigDecimal.valueOf(subtotalNum.doubleValue()) : BigDecimal.ZERO;
        BigDecimal discount = discountNum != null ? BigDecimal.valueOf(discountNum.doubleValue()) : BigDecimal.ZERO;
        BigDecimal total = totalAmountNum != null ? BigDecimal.valueOf(totalAmountNum.doubleValue()) : subtotal.subtract(discount);
        if (total.compareTo(BigDecimal.ZERO) < 0) total = BigDecimal.ZERO;

        // 4. Sinh mã đơn đặt vé duy nhất
        String bookingCode = "CNE" + (System.currentTimeMillis() % 10000000L);

        // 5. Lưu đơn Booking ở trạng thái HELD kèm thời gian giữ 10 phút
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setShowtime(showtime);
        booking.setBookingCode(bookingCode);
        booking.setStatus(BookingStatus.HELD);
        booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(10));
        booking.setSubtotal(subtotal);
        booking.setDiscountAmount(discount);
        booking.setTotalAmount(total);

        booking = bookingRepository.save(booking);

        List<?> seatIdsRaw = (List<?>) request.get("seatIds");
        if (seatIdsRaw != null && !seatIdsRaw.isEmpty()) {
            for (Object obj : seatIdsRaw) {
                if (obj instanceof Number) {
                    Long sId = ((Number) obj).longValue();
                    Seat seat = seatRepository.findById(sId).orElse(null);
                    if (seat != null) {
                        BookingSeat bs = new BookingSeat();
                        bs.setBooking(booking);
                        bs.setSeat(seat);
                        bs.setSeatPrice(booking.getTotalAmount());
                        bs.setSeatType(seat.getSeatType() != null ? seat.getSeatType().name() : "NORMAL");
                        bookingSeatRepository.save(bs);
                    }
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", booking.getBookingId());
        result.put("bookingCode", booking.getBookingCode());
        result.put("status", booking.getStatus().name());
        result.put("totalAmount", booking.getTotalAmount());
        result.put("holdExpiresAt", booking.getHoldExpiresAt() != null ? booking.getHoldExpiresAt().toString() : null);
        result.put("showtimeId", showtimeId);
        return result;
    }
}