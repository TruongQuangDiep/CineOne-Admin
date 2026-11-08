package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.ticket.TicketResponse;
import com.example.cineone_admin.entity.Booking;
import com.example.cineone_admin.entity.BookingSeat;
import com.example.cineone_admin.entity.Cinema;
import com.example.cineone_admin.entity.Movie;
import com.example.cineone_admin.entity.Room;
import com.example.cineone_admin.entity.Showtime;
import com.example.cineone_admin.entity.Ticket;
import com.example.cineone_admin.entity.User;
import com.example.cineone_admin.repository.BookingSeatRepository;
import com.example.cineone_admin.repository.TicketRepository;
import com.example.cineone_admin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final EmailService emailService;
    private final BookingSeatRepository bookingSeatRepository;

    /**
     * Tự động tạo vé khi Booking thanh toán thành công
     */
    @Transactional
    public Ticket createTicketForBooking(Booking booking) {
        if (booking == null) return null;

        // Tránh tạo trùng vé cho cùng 1 đơn đặt
        var existing = ticketRepository.findAll().stream()
                .filter(t -> t.getBooking() != null && t.getBooking().getBookingId().equals(booking.getBookingId()))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }

        Showtime showtime = booking.getShowtime();
        Movie movie = showtime != null ? showtime.getMovie() : null;
        Room room = showtime != null ? showtime.getRoom() : null;
        Cinema cinema = room != null ? room.getCinema() : null;

        Ticket ticket = new Ticket();
        ticket.setBooking(booking);
        ticket.setTicketCode(booking.getBookingCode() != null ? booking.getBookingCode() : "TCK" + System.currentTimeMillis());
        ticket.setMovieTitle(movie != null ? movie.getTitle() : "Phim chiếu rạp");
        ticket.setCinemaName(cinema != null ? cinema.getName() : "CineOne Cinema");
        ticket.setRoomName(room != null ? room.getName() : "Phòng chiếu 01");
        ticket.setShowtimeStart(showtime != null ? showtime.getStartTime() : LocalDateTime.now());
        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBooking_BookingId(booking.getBookingId());
        String realSeatInfo = bookingSeats.isEmpty()
                ? "Ghế tiêu chuẩn"
                : bookingSeats.stream()
                    .map(bs -> bs.getSeat().getSeatRow() + bs.getSeat().getSeatNumber())
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(", "));
        ticket.setSeatInfo(realSeatInfo);
        ticket.setTotalAmount(booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO);
        ticket.setStatus("VALID");

        Ticket savedTicket = ticketRepository.save(ticket);
        // Tự động bắn email vé điện tử về Gmail tài khoản người dùng
        if (booking.getUser() != null && booking.getUser().getEmail() != null) {
            emailService.sendTicketEmail(
                    booking.getUser().getEmail(),
                    booking.getUser().getFullName(),
                    savedTicket
            );
        }
        return savedTicket;
    }

    /**
     * Lấy danh sách vé của người dùng hiện tại (bắt buộc đăng nhập)
     */
    public List<TicketResponse> getMyTickets(String tab) {
        User user = getAuthenticatedUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để xem vé của bạn.");
        }

        LocalDateTime now = LocalDateTime.now();
        return ticketRepository.findByBooking_User_UserIdOrderByShowtimeStartAsc(user.getUserId())
                .stream()
                .filter(ticket -> filterByTab(ticket, tab, now))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Xem chi tiết vé - chỉ chủ sở hữu vé mới có quyền xem
     */
    public TicketResponse getTicketDetail(Long ticketId) {
        User user = getAuthenticatedUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để xem vé.");
        }

        Ticket ticket = ticketRepository.findByTicketIdAndBooking_User_UserId(ticketId, user.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy vé hoặc bạn không có quyền xem vé này."));

        return toResponse(ticket);
    }

    private boolean filterByTab(Ticket ticket, String tab, LocalDateTime now) {
        LocalDateTime showtime = ticket.getShowtimeStart();
        String status = ticket.getStatus() == null ? "" : ticket.getStatus().trim().toUpperCase();
        String bookingStatus = ticket.getBooking() == null || ticket.getBooking().getStatus() == null
                ? ""
                : ticket.getBooking().getStatus().name().trim().toUpperCase();

        boolean cancelled = "CANCELLED".equals(status) || "EXPIRED".equals(status)
                || "CANCELLED".equals(bookingStatus) || "EXPIRED".equals(bookingStatus);
        boolean terminal = "USED".equals(status) || "COMPLETED".equals(status) || "VALID".equals(status) && showtime != null && showtime.isBefore(now)
                || cancelled;

        if ("UPCOMING".equalsIgnoreCase(tab)) {
            return showtime != null && !showtime.isBefore(now) && !terminal;
        }

        if ("HISTORY".equalsIgnoreCase(tab)) {
            return showtime == null || showtime.isBefore(now) || terminal;
        }

        return true;
    }

    private TicketResponse toResponse(Ticket ticket) {
        Booking booking = ticket.getBooking();
        Showtime showtime = booking != null ? booking.getShowtime() : null;
        Movie movie = showtime != null ? showtime.getMovie() : null;
        Room room = showtime != null ? showtime.getRoom() : null;
        Cinema cinema = room != null ? room.getCinema() : null;

        LocalDateTime showDateTime = ticket.getShowtimeStart();
        String showDate = showDateTime != null ? showDateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : null;
        String showTime = showDateTime != null ? showDateTime.format(DateTimeFormatter.ofPattern("HH:mm")) : null;
        String showDateTimeText = showDateTime != null ? showDateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : null;

        TicketResponse ticketResponse = TicketResponse.builder()
                .ticketId(ticket.getTicketId())
                .ticketCode(ticket.getTicketCode())
                .bookingCode(booking != null ? booking.getBookingCode() : ticket.getTicketCode())
                .movieTitle(ticket.getMovieTitle() != null ? ticket.getMovieTitle() : (movie != null ? movie.getTitle() : null))
                .moviePoster(movie != null ? movie.getPosterUrl() : null)
                .cinemaName(ticket.getCinemaName() != null ? ticket.getCinemaName() : (cinema != null ? cinema.getName() : null))
                .roomName(ticket.getRoomName() != null ? ticket.getRoomName() : (room != null ? room.getName() : null))
                .seats(ticket.getSeatInfo())
                .seatCodes(ticket.getSeatInfo())
                .showtime(showDateTime)
                .showDate(showDate)
                .showTime(showTime)
                .showDateTime(showDateTimeText)
                .totalAmount(ticket.getTotalAmount())
                .status(ticket.getStatus())
                .qrCode(ticket.getTicketCode())
                .qrCheckInCode(booking != null ? booking.getBookingCode() : ticket.getTicketCode())
                .build();

        if (booking != null) {
            ticketResponse.setTicketCode(booking.getBookingCode());
            ticketResponse.setSeatCodes(ticket.getSeatInfo());
            ticketResponse.setQrCheckInCode(booking.getBookingCode());
        }

        return ticketResponse;
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        String principal = authentication.getName();
        if (principal.contains("@")) {
            return userRepository.findByEmail(principal).orElse(null);
        }
        try {
            Long userId = Long.parseLong(principal);
            return userRepository.findById(userId).orElse(null);
        } catch (NumberFormatException e) {
            return userRepository.findByEmail(principal).orElse(null);
        }
    }
}