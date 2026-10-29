package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.booking.BookingResponse;
import com.example.cineone_admin.dto.booking.UpdateBookingStatusRequest;
import com.example.cineone_admin.entity.Booking;
import com.example.cineone_admin.enums.BookingStatus;
import com.example.cineone_admin.exception.ResourceNotFoundException;
import com.example.cineone_admin.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public Page<BookingResponse> getBookings(String keyword, BookingStatus status, Pageable pageable) {
        return bookingRepository.searchBookings(keyword, status, pageable).map(this::mapToResponse);
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
            booking.setStatus(request.getStatus().name());
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
            try {
                res.setStatus(BookingStatus.valueOf(booking.getStatus().toUpperCase()));
            } catch (IllegalArgumentException e) {
                res.setStatus(BookingStatus.PENDING);
            }
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
}