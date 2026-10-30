package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.booking.BookingResponse;
import com.example.cineone_admin.dto.booking.UpdateBookingStatusRequest;
import com.example.cineone_admin.entity.*;
import com.example.cineone_admin.enums.BookingStatus;
import com.example.cineone_admin.exception.ResourceNotFoundException;
import com.example.cineone_admin.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTests {

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private BookingService bookingService;

    private Booking mockBooking;

    @BeforeEach
    void setUp() {
        mockBooking = new Booking();
        mockBooking.setBookingId(1L);
        mockBooking.setBookingCode("BK-TEST01");
        mockBooking.setSubtotal(BigDecimal.valueOf(100000));
        mockBooking.setDiscountAmount(BigDecimal.ZERO);
        mockBooking.setTotalAmount(BigDecimal.valueOf(100000));
        mockBooking.setStatus(BookingStatus.CONFIRMED);
        mockBooking.setCreatedAt(LocalDateTime.now());

        User user = new User();
        user.setUserId(10L);
        user.setFullName("Nguyễn Văn A");
        user.setPhone("0901234567");
        mockBooking.setUser(user);

        Cinema cinema = new Cinema();
        cinema.setName("CGV Landmark 81");

        Room room = new Room();
        room.setName("Cinema 01");
        room.setCinema(cinema);

        Movie movie = new Movie();
        movie.setTitle("Mai");

        Showtime showtime = new Showtime();
        showtime.setShowtimeId(20L);
        showtime.setStartTime(LocalDateTime.now().plusDays(1));
        showtime.setMovie(movie);
        showtime.setRoom(room);
        mockBooking.setShowtime(showtime);
    }

    @Test
    void getBookings_ReturnsMappedPageSuccessfully() {
        var pageable = PageRequest.of(0, 10);
        when(bookingRepository.searchBookings(eq("BK-TEST"), eq(BookingStatus.CONFIRMED), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(mockBooking)));

                Page<BookingResponse> result = bookingService.getBookings("BK-TEST", BookingStatus.CONFIRMED.name(), pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        BookingResponse dto = result.getContent().get(0);
        assertEquals("BK-TEST01", dto.getBookingCode());
        assertEquals("Nguyễn Văn A", dto.getCustomerName());
        assertEquals("Mai", dto.getMovieTitle());
        assertEquals("CGV Landmark 81", dto.getCinemaName());
        assertEquals("Cinema 01", dto.getRoomName());
        assertEquals(BookingStatus.CONFIRMED, dto.getStatus());
    }

    @Test
    void getBookingById_ThrowsExceptionWhenNotFound() {
        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bookingService.getBookingById(999L));
    }

    @Test
    void updateBookingStatus_UpdatesEntitySuccessfully() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(mockBooking));

        UpdateBookingStatusRequest request = new UpdateBookingStatusRequest();
        request.setStatus(BookingStatus.CANCELLED);

        bookingService.updateBookingStatus(1L, request);

        assertEquals(BookingStatus.CANCELLED, mockBooking.getStatus());
        verify(bookingRepository, times(1)).save(mockBooking);
    }

    @Test
    void mapToResponse_HandlesNullRelationsWithoutNPE() {
        Booking orphan = new Booking();
        orphan.setBookingId(2L);
        orphan.setBookingCode("BK-ORPHAN");
        orphan.setStatus(BookingStatus.PENDING); 

        BookingResponse dto = bookingService.mapToResponse(orphan);

        assertNotNull(dto);
        assertEquals("BK-ORPHAN", dto.getBookingCode());
        assertNull(dto.getCustomerName());
        assertNull(dto.getMovieTitle());
        assertEquals(BookingStatus.PENDING, dto.getStatus()); // Fallback thành công
    }
}