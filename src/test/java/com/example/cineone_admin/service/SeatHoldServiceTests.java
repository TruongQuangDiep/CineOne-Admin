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
import com.google.api.core.ApiFuture;
import com.google.api.core.SettableApiFuture;
import com.google.cloud.firestore.*;
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
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
        ReflectionTestUtils.setField(seatHoldService, "firestore", null);

        SeatHoldRequest request = new SeatHoldRequest();
        request.setShowtimeId(100L);
        request.setSeatIds(List.of(1L, 2L));
        request.setUserId(99L);

        BusinessException ex = assertThrows(BusinessException.class, () -> seatHoldService.holdSeats(request));
        assertTrue(ex.getMessage().contains("Firestore chưa được cấu hình"));
    }

    @Test
    @DisplayName("Ném ngoại lệ khi số lượng ghế giữ vượt quá giới hạn tối đa cho phép (8 ghế)")
    void holdSeats_ShouldThrowBusinessException_WhenExceedsMaxSeats() {
        SeatHoldRequest request = new SeatHoldRequest();
        request.setShowtimeId(100L);
        request.setSeatIds(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L));
        request.setUserId(99L);

        BusinessException ex = assertThrows(BusinessException.class, () -> seatHoldService.holdSeats(request));
        assertTrue(ex.getMessage().contains("tối đa 8 ghế"));
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

    @Test
    @DisplayName("Ném ngoại lệ khi số lượng ghế nhả vượt quá giới hạn tối đa cho phép (8 ghế)")
    void releaseSeats_ShouldThrowBusinessException_WhenExceedsMaxSeats() {
        BusinessException ex = assertThrows(BusinessException.class, () -> 
            seatHoldService.releaseSeats(100L, 99L, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L))
        );
        assertTrue(ex.getMessage().contains("vượt quá giới hạn (8 ghế)"));
    }

    @Test
    @DisplayName("Ném ngoại lệ khi nhả ghế do người khác giữ")
    void releaseSeats_ShouldThrowBusinessException_WhenSeatBelongsToDifferentUser() throws Exception {
        CollectionReference colRef = mock(CollectionReference.class);
        DocumentReference showtimeDocRef = mock(DocumentReference.class);
        CollectionReference heldSeatsColRef = mock(CollectionReference.class);
        DocumentReference seatDocRef = mock(DocumentReference.class);
        Transaction transaction = mock(Transaction.class);
        DocumentSnapshot snapshot = mock(DocumentSnapshot.class);
        ApiFuture<DocumentSnapshot> futureSnapshot = mock(ApiFuture.class);

        when(firestore.collection("showtimes")).thenReturn(colRef);
        when(colRef.document("100")).thenReturn(showtimeDocRef);
        when(showtimeDocRef.collection("held_seats")).thenReturn(heldSeatsColRef);
        when(heldSeatsColRef.document("1")).thenReturn(seatDocRef);

        when(transaction.get(seatDocRef)).thenReturn(futureSnapshot);
        when(futureSnapshot.get()).thenReturn(snapshot);
        when(snapshot.exists()).thenReturn(true);
        when(snapshot.getString("status")).thenReturn("HOLDING"); // Đảm bảo trạng thái hợp lệ
        when(snapshot.contains("userId")).thenReturn(true);
        when(snapshot.getLong("userId")).thenReturn(888L); // Ghế đang do user 888 giữ

        when(firestore.runTransaction(any())).thenAnswer(invocation -> {
            Transaction.Function<?> callback = invocation.getArgument(0);
            SettableApiFuture<Object> settable = SettableApiFuture.create();
            try {
                Object result = callback.updateCallback(transaction);
                settable.set(result);
            } catch (Exception e) {
                settable.setException(new ExecutionException(e));
            }
            return settable;
        });

        BusinessException ex = assertThrows(BusinessException.class, () -> 
            seatHoldService.releaseSeats(100L, 999L, List.of(1L))
        );

        assertTrue(ex.getMessage().contains("không thuộc quyền giữ của bạn"));
        verify(transaction, never()).delete(any());
    }

    @Test
    @DisplayName("Ném ngoại lệ và không cho phép nhả ghế khi trạng thái là BOOKED")
    void releaseSeats_ShouldThrowBusinessException_WhenSeatStatusIsBooked() throws Exception {
        CollectionReference colRef = mock(CollectionReference.class);
        DocumentReference showtimeDocRef = mock(DocumentReference.class);
        CollectionReference heldSeatsColRef = mock(CollectionReference.class);
        DocumentReference seatDocRef = mock(DocumentReference.class);
        Transaction transaction = mock(Transaction.class);
        DocumentSnapshot snapshot = mock(DocumentSnapshot.class);
        ApiFuture<DocumentSnapshot> futureSnapshot = mock(ApiFuture.class);

        when(firestore.collection("showtimes")).thenReturn(colRef);
        when(colRef.document("100")).thenReturn(showtimeDocRef);
        when(showtimeDocRef.collection("held_seats")).thenReturn(heldSeatsColRef);
        when(heldSeatsColRef.document("1")).thenReturn(seatDocRef);

        when(transaction.get(seatDocRef)).thenReturn(futureSnapshot);
        when(futureSnapshot.get()).thenReturn(snapshot);
        when(snapshot.exists()).thenReturn(true);
        when(snapshot.getString("status")).thenReturn("BOOKED");

        when(firestore.runTransaction(any())).thenAnswer(invocation -> {
            Transaction.Function<?> callback = invocation.getArgument(0);
            SettableApiFuture<Object> settable = SettableApiFuture.create();
            try {
                Object result = callback.updateCallback(transaction);
                settable.set(result);
            } catch (Exception e) {
                settable.setException(new ExecutionException(e));
            }
            return settable;
        });

        BusinessException ex = assertThrows(BusinessException.class, () -> 
            seatHoldService.releaseSeats(100L, 99L, List.of(1L))
        );

        assertTrue(ex.getMessage().contains("không ở trạng thái giữ chỗ hợp lệ"));
        verify(transaction, never()).delete(any());
    }

    @Test
    @DisplayName("Ném ngoại lệ khi nhả ghế có trạng thái status bị null")
    void releaseSeats_ShouldThrowBusinessException_WhenStatusIsNull() throws Exception {
        CollectionReference colRef = mock(CollectionReference.class);
        DocumentReference showtimeDocRef = mock(DocumentReference.class);
        CollectionReference heldSeatsColRef = mock(CollectionReference.class);
        DocumentReference seatDocRef = mock(DocumentReference.class);
        Transaction transaction = mock(Transaction.class);
        DocumentSnapshot snapshot = mock(DocumentSnapshot.class);
        ApiFuture<DocumentSnapshot> futureSnapshot = mock(ApiFuture.class);

        when(firestore.collection("showtimes")).thenReturn(colRef);
        when(colRef.document("100")).thenReturn(showtimeDocRef);
        when(showtimeDocRef.collection("held_seats")).thenReturn(heldSeatsColRef);
        when(heldSeatsColRef.document("1")).thenReturn(seatDocRef);

        when(transaction.get(seatDocRef)).thenReturn(futureSnapshot);
        when(futureSnapshot.get()).thenReturn(snapshot);
        when(snapshot.exists()).thenReturn(true);
        when(snapshot.getString("status")).thenReturn(null); // Status null

        when(firestore.runTransaction(any())).thenAnswer(invocation -> {
            Transaction.Function<?> callback = invocation.getArgument(0);
            SettableApiFuture<Object> settable = SettableApiFuture.create();
            try {
                Object result = callback.updateCallback(transaction);
                settable.set(result);
            } catch (Exception e) {
                settable.setException(new ExecutionException(e));
            }
            return settable;
        });

        BusinessException ex = assertThrows(BusinessException.class, () -> 
            seatHoldService.releaseSeats(100L, 99L, List.of(1L))
        );

        assertTrue(ex.getMessage().contains("không ở trạng thái giữ chỗ hợp lệ"));
        verify(transaction, never()).delete(any());
    }

    @Test
    @DisplayName("Ném ngoại lệ khi nhả ghế thiếu hoàn toàn thông tin chủ sở hữu (cả userId và user_id đều null)")
    void releaseSeats_ShouldThrowBusinessException_WhenOwnerIsMissing() throws Exception {
        CollectionReference colRef = mock(CollectionReference.class);
        DocumentReference showtimeDocRef = mock(DocumentReference.class);
        CollectionReference heldSeatsColRef = mock(CollectionReference.class);
        DocumentReference seatDocRef = mock(DocumentReference.class);
        Transaction transaction = mock(Transaction.class);
        DocumentSnapshot snapshot = mock(DocumentSnapshot.class);
        ApiFuture<DocumentSnapshot> futureSnapshot = mock(ApiFuture.class);

        when(firestore.collection("showtimes")).thenReturn(colRef);
        when(colRef.document("100")).thenReturn(showtimeDocRef);
        when(showtimeDocRef.collection("held_seats")).thenReturn(heldSeatsColRef);
        when(heldSeatsColRef.document("1")).thenReturn(seatDocRef);

        when(transaction.get(seatDocRef)).thenReturn(futureSnapshot);
        when(futureSnapshot.get()).thenReturn(snapshot);
        when(snapshot.exists()).thenReturn(true);
        when(snapshot.getString("status")).thenReturn("HOLDING");
        when(snapshot.contains("userId")).thenReturn(false);

        when(firestore.runTransaction(any())).thenAnswer(invocation -> {
            Transaction.Function<?> callback = invocation.getArgument(0);
            SettableApiFuture<Object> settable = SettableApiFuture.create();
            try {
                Object result = callback.updateCallback(transaction);
                settable.set(result);
            } catch (Exception e) {
                settable.setException(new ExecutionException(e));
            }
            return settable;
        });

        BusinessException ex = assertThrows(BusinessException.class, () -> 
            seatHoldService.releaseSeats(100L, 99L, List.of(1L))
        );

        assertTrue(ex.getMessage().contains("không thuộc quyền giữ của bạn"));
        verify(transaction, never()).delete(any());
    }

    @Test
    @DisplayName("Đảm bảo tính nguyên tử: Nếu 1 trong các ghế thuộc người khác, hủy giao dịch và không xóa bất kỳ ghế nào")
    void releaseSeats_ShouldRollbackEntirely_WhenOneSeatBelongsToAnotherUser() throws Exception {
        CollectionReference colRef = mock(CollectionReference.class);
        DocumentReference showtimeDocRef = mock(DocumentReference.class);
        CollectionReference heldSeatsColRef = mock(CollectionReference.class);
        DocumentReference seatDoc1 = mock(DocumentReference.class);
        DocumentReference seatDoc2 = mock(DocumentReference.class);
        Transaction transaction = mock(Transaction.class);
        DocumentSnapshot snap1 = mock(DocumentSnapshot.class);
        DocumentSnapshot snap2 = mock(DocumentSnapshot.class);
        ApiFuture<DocumentSnapshot> fSnap1 = mock(ApiFuture.class);
        ApiFuture<DocumentSnapshot> fSnap2 = mock(ApiFuture.class);

        when(firestore.collection("showtimes")).thenReturn(colRef);
        when(colRef.document("100")).thenReturn(showtimeDocRef);
        when(showtimeDocRef.collection("held_seats")).thenReturn(heldSeatsColRef);
        when(heldSeatsColRef.document("1")).thenReturn(seatDoc1);
        when(heldSeatsColRef.document("2")).thenReturn(seatDoc2);

        when(transaction.get(seatDoc1)).thenReturn(fSnap1);
        when(fSnap1.get()).thenReturn(snap1);
        when(snap1.exists()).thenReturn(true);
        when(snap1.getString("status")).thenReturn("HOLDING");
        when(snap1.contains("userId")).thenReturn(true);
        when(snap1.getLong("userId")).thenReturn(99L);

        when(transaction.get(seatDoc2)).thenReturn(fSnap2);
        when(fSnap2.get()).thenReturn(snap2);
        when(snap2.exists()).thenReturn(true);
        when(snap2.getString("status")).thenReturn("HOLDING");
        when(snap2.contains("userId")).thenReturn(true);
        when(snap2.getLong("userId")).thenReturn(888L);

        when(firestore.runTransaction(any())).thenAnswer(invocation -> {
            Transaction.Function<?> callback = invocation.getArgument(0);
            SettableApiFuture<Object> settable = SettableApiFuture.create();
            try {
                Object result = callback.updateCallback(transaction);
                settable.set(result);
            } catch (Exception e) {
                settable.setException(new ExecutionException(e));
            }
            return settable;
        });

        assertThrows(BusinessException.class, () -> 
            seatHoldService.releaseSeats(100L, 99L, List.of(1L, 2L))
        );

        verify(transaction, never()).delete(any());
    }

    @Test
    @DisplayName("Ném ngoại lệ khi danh sách ghế nhả chứa phần tử null")
    void releaseSeats_ShouldThrowBusinessException_WhenSeatIdsContainNull() {
        List<Long> seatIdsWithNull = new java.util.ArrayList<>();
        seatIdsWithNull.add(1L);
        seatIdsWithNull.add(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> 
            seatHoldService.releaseSeats(100L, 99L, seatIdsWithNull)
        );
        assertTrue(ex.getMessage().contains("không được chứa giá trị rỗng"));
    }

    @Test
    @DisplayName("Ném ngoại lệ khi danh sách ghế nhả chứa ID trùng lặp")
    void releaseSeats_ShouldThrowBusinessException_WhenSeatIdsContainDuplicates() {
        BusinessException ex = assertThrows(BusinessException.class, () -> 
            seatHoldService.releaseSeats(100L, 99L, List.of(1L, 1L))
        );
        assertTrue(ex.getMessage().contains("không được chứa ghế trùng lặp"));
    }
}