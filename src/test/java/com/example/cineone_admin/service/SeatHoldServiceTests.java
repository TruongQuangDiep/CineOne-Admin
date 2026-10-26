package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.hold.SeatHoldRequest;
import com.example.cineone_admin.entity.Room;
import com.example.cineone_admin.entity.Seat;
import com.example.cineone_admin.entity.Showtime;
import com.example.cineone_admin.enums.SeatStatus;
import com.example.cineone_admin.exception.BusinessException;
import com.example.cineone_admin.exception.ResourceNotFoundException;
import com.example.cineone_admin.repository.SeatRepository;
import com.example.cineone_admin.repository.ShowtimeRepository;
import com.google.cloud.firestore.Firestore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatHoldServiceTests {

    @Mock
    private ShowtimeRepository showtimeRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private Firestore firestore;

    @InjectMocks
    private SeatHoldService seatHoldService;

    private Showtime sampleShowtime;
    private Room sampleRoom;
    private Seat seat1;
    private Seat seat2;

    @BeforeEach
    void setUp() {
        // Luôn gán Mock firestore hợp lệ trước mỗi bài test
        ReflectionTestUtils.setField(seatHoldService, "firestore", firestore);

        sampleRoom = new Room();
        sampleRoom.setRoomId(10L);

        sampleShowtime = new Showtime();
        sampleShowtime.setShowtimeId(100L);
        sampleShowtime.setRoom(sampleRoom);

        seat1 = new Seat();
        seat1.setSeatId(1L);
        seat1.setRoom(sampleRoom);
        seat1.setSeatRow("A");
        seat1.setSeatNumber(5);
        seat1.setStatus(SeatStatus.ACTIVE);

        seat2 = new Seat();
        seat2.setSeatId(2L);
        seat2.setRoom(sampleRoom);
        seat2.setSeatRow("A");
        seat2.setSeatNumber(6);
        seat2.setStatus(SeatStatus.ACTIVE);
    }

    @Test
    @DisplayName("Ném ngoại lệ khi Firestore chưa được khởi tạo (thiếu file JSON)")
    void holdSeats_ShouldThrowBusinessException_WhenFirestoreIsNull() {
        // Chỉ riêng test case này set null để kiểm tra nhánh validate cấu hình
        ReflectionTestUtils.setField(seatHoldService, "firestore", null);

        SeatHoldRequest request = new SeatHoldRequest();
        request.setShowtimeId(100L);
        request.setSeatIds(List.of(1L, 2L));
        request.setUserId(99L);

        BusinessException ex = assertThrows(BusinessException.class, () -> seatHoldService.holdSeats(request));
        assertTrue(ex.getMessage().contains("Firestore chưa được cấu hình"));
    }

    @Test
    @DisplayName("Ném ngoại lệ khi suất chiếu không tồn tại trong hệ thống")
    void holdSeats_ShouldThrowNotFoundException_WhenShowtimeNotFound() {
        SeatHoldRequest request = new SeatHoldRequest();
        request.setShowtimeId(999L);
        request.setSeatIds(List.of(1L));
        request.setUserId(99L);

        when(showtimeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> seatHoldService.holdSeats(request));
        verify(showtimeRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("Ném ngoại lệ khi một số ghế gửi lên không tồn tại trong DB")
    void holdSeats_ShouldThrowBusinessException_WhenSomeSeatsNotFound() {
        SeatHoldRequest request = new SeatHoldRequest();
        request.setShowtimeId(100L);
        request.setSeatIds(List.of(1L, 999L));
        request.setUserId(99L);

        when(showtimeRepository.findById(100L)).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findAllById(request.getSeatIds())).thenReturn(List.of(seat1));

        BusinessException ex = assertThrows(BusinessException.class, () -> seatHoldService.holdSeats(request));
        assertEquals("Một số ghế yêu cầu không tồn tại trong hệ thống.", ex.getMessage());
    }

    @Test
    @DisplayName("Ném ngoại lệ khi ghế không thuộc phòng của suất chiếu đó")
    void holdSeats_ShouldThrowBusinessException_WhenSeatBelongsToDifferentRoom() {
        Room otherRoom = new Room();
        otherRoom.setRoomId(20L);
        seat2.setRoom(otherRoom);

        SeatHoldRequest request = new SeatHoldRequest();
        request.setShowtimeId(100L);
        request.setSeatIds(List.of(1L, 2L));
        request.setUserId(99L);

        when(showtimeRepository.findById(100L)).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findAllById(request.getSeatIds())).thenReturn(List.of(seat1, seat2));

        BusinessException ex = assertThrows(BusinessException.class, () -> seatHoldService.holdSeats(request));
        assertTrue(ex.getMessage().contains("không thuộc phòng chiếu này"));
    }

    @Test
    @DisplayName("Ném ngoại lệ khi ghế đang bảo trì hoặc ngừng sử dụng")
    void holdSeats_ShouldThrowBusinessException_WhenSeatIsMaintenance() {
        seat2.setStatus(SeatStatus.MAINTENANCE);

        SeatHoldRequest request = new SeatHoldRequest();
        request.setShowtimeId(100L);
        request.setSeatIds(List.of(1L, 2L));
        request.setUserId(99L);

        when(showtimeRepository.findById(100L)).thenReturn(Optional.of(sampleShowtime));
        when(seatRepository.findAllById(request.getSeatIds())).thenReturn(List.of(seat1, seat2));

        BusinessException ex = assertThrows(BusinessException.class, () -> seatHoldService.holdSeats(request));
        assertTrue(ex.getMessage().contains("đang bảo trì hoặc ngừng sử dụng"));
    }

    @Test
    @DisplayName("Scheduled Worker chạy an toàn không lỗi khi Firestore chưa khởi tạo")
    void autoReleaseExpiredHolds_ShouldNotThrow_WhenFirestoreIsNull() {
        ReflectionTestUtils.setField(seatHoldService, "firestore", null);
        assertDoesNotThrow(() -> seatHoldService.autoReleaseExpiredHolds());
    }
}