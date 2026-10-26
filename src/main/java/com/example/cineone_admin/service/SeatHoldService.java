package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.hold.HeldSeatDTO;
import com.example.cineone_admin.dto.hold.SeatHoldRequest;
import com.example.cineone_admin.dto.hold.SeatHoldResponse;
import com.example.cineone_admin.entity.Seat;
import com.example.cineone_admin.entity.Showtime;
import com.example.cineone_admin.enums.SeatStatus;
import com.example.cineone_admin.exception.BusinessException;
import com.example.cineone_admin.exception.ResourceNotFoundException;
import com.example.cineone_admin.repository.SeatRepository;
import com.example.cineone_admin.repository.ShowtimeRepository;
import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatHoldService {

    @Autowired(required = false)
    private Firestore firestore;

    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;

    private static final long HOLD_TTL_MINUTES = 10;
    private static final String COLLECTION_SHOWTIMES = "showtimes";
    private static final String SUB_COLLECTION_HELD_SEATS = "held_seats";

    public SeatHoldResponse holdSeats(SeatHoldRequest request) {
        checkFirestoreReady();

        Showtime showtime = showtimeRepository.findById(request.getShowtimeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy suất chiếu ID: " + request.getShowtimeId()));

        List<Seat> seats = seatRepository.findAllById(request.getSeatIds());
        if (seats.size() != request.getSeatIds().size()) {
            throw new BusinessException("Một số ghế yêu cầu không tồn tại trong hệ thống.");
        }

        Long roomId = showtime.getRoom().getRoomId();
        for (Seat s : seats) {
            if (!s.getRoom().getRoomId().equals(roomId)) {
                throw new BusinessException("Ghế ID " + s.getSeatId() + " không thuộc phòng chiếu này.");
            }
            if (s.getStatus() != SeatStatus.ACTIVE) {
                throw new BusinessException("Ghế " + s.getSeatRow() + s.getSeatNumber() + " đang bảo trì hoặc ngừng sử dụng.");
            }
        }

        Map<Long, String> seatCodeMap = new HashMap<>();
        seats.forEach(s -> seatCodeMap.put(s.getSeatId(), s.getSeatRow() + s.getSeatNumber()));

        CollectionReference heldSeatsRef = firestore.collection(COLLECTION_SHOWTIMES)
                .document(String.valueOf(request.getShowtimeId()))
                .collection(SUB_COLLECTION_HELD_SEATS);

        Instant now = Instant.now();
        Instant expiresAtInstant = now.plus(Duration.ofMinutes(HOLD_TTL_MINUTES));
        Timestamp expiresAtTimestamp = Timestamp.ofTimeSecondsAndNanos(expiresAtInstant.getEpochSecond(), expiresAtInstant.getNano());
        Timestamp nowTimestamp = Timestamp.now();

        try {
            firestore.runTransaction(transaction -> {
                Map<DocumentReference, DocumentSnapshot> snapshots = new HashMap<>();
                for (Long seatId : request.getSeatIds()) {
                    DocumentReference docRef = heldSeatsRef.document(String.valueOf(seatId));
                    DocumentSnapshot snapshot = transaction.get(docRef).get();
                    snapshots.put(docRef, snapshot);
                }

                for (Map.Entry<DocumentReference, DocumentSnapshot> entry : snapshots.entrySet()) {
                    DocumentSnapshot snap = entry.getValue();
                    if (snap.exists()) {
                        String status = snap.getString("status");
                        Long holdingUserId = snap.getLong("userId");
                        Timestamp holdExpiresAt = snap.getTimestamp("holdExpiresAt");
                        String seatCode = snap.getString("seatCode");

                        if ("BOOKED".equalsIgnoreCase(status)) {
                            throw new BusinessException("Ghế " + seatCode + " đã được đặt và thanh toán thành công.");
                        }

                        if ("HOLDING".equalsIgnoreCase(status) && holdExpiresAt != null) {
                            boolean isExpired = holdExpiresAt.compareTo(nowTimestamp) <= 0;
                            boolean isSameUser = Objects.equals(holdingUserId, request.getUserId());

                            if (!isExpired && !isSameUser) {
                                throw new BusinessException("Ghế " + seatCode + " đang được người khác chọn.");
                            }
                        }
                    }
                }

                for (Seat seat : seats) {
                    DocumentReference docRef = heldSeatsRef.document(String.valueOf(seat.getSeatId()));
                    Map<String, Object> data = new HashMap<>();
                    data.put("seatId", seat.getSeatId());
                    data.put("showtimeId", request.getShowtimeId());
                    data.put("seatCode", seatCodeMap.get(seat.getSeatId()));
                    data.put("userId", request.getUserId());
                    data.put("status", "HOLDING");
                    data.put("holdExpiresAt", expiresAtTimestamp);
                    data.put("createdAt", nowTimestamp);

                    transaction.set(docRef, data);
                }
                return null;
            }).get();

        } catch (InterruptedException | ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof BusinessException be) {
                throw be;
            }
            log.error("Lỗi Transaction giữ ghế: ", e);
            throw new BusinessException("Xảy ra xung đột khi giữ ghế. Vui lòng thử lại.");
        }

        return SeatHoldResponse.builder()
                .showtimeId(request.getShowtimeId())
                .userId(request.getUserId())
                .heldSeatIds(request.getSeatIds())
                .heldSeatCodes(seats.stream().map(s -> seatCodeMap.get(s.getSeatId())).toList())
                .expiresAt(expiresAtInstant)
                .remainingSeconds(HOLD_TTL_MINUTES * 60)
                .message("Giữ ghế thành công trong 10 phút.")
                .build();
    }

    public void releaseSeats(Long showtimeId, List<Long> seatIds, Long userId) {
        checkFirestoreReady();
        CollectionReference heldSeatsRef = firestore.collection(COLLECTION_SHOWTIMES)
                .document(String.valueOf(showtimeId))
                .collection(SUB_COLLECTION_HELD_SEATS);

        try {
            firestore.runTransaction(transaction -> {
                for (Long seatId : seatIds) {
                    DocumentReference docRef = heldSeatsRef.document(String.valueOf(seatId));
                    DocumentSnapshot snap = transaction.get(docRef).get();
                    if (snap.exists()) {
                        Long holdingUserId = snap.getLong("userId");
                        String status = snap.getString("status");
                        if ("HOLDING".equalsIgnoreCase(status) && Objects.equals(holdingUserId, userId)) {
                            transaction.delete(docRef);
                        }
                    }
                }
                return null;
            }).get();
        } catch (Exception e) {
            log.error("Lỗi khi nhả ghế: ", e);
            throw new BusinessException("Không thể nhả ghế. Vui lòng thử lại.");
        }
    }

    public List<HeldSeatDTO> getHeldSeats(Long showtimeId) {
        checkFirestoreReady();
        List<HeldSeatDTO> result = new ArrayList<>();
        Timestamp now = Timestamp.now();

        try {
            ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_SHOWTIMES)
                    .document(String.valueOf(showtimeId))
                    .collection(SUB_COLLECTION_HELD_SEATS)
                    .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                String status = doc.getString("status");
                Timestamp expiresAt = doc.getTimestamp("holdExpiresAt");

                if ("HOLDING".equalsIgnoreCase(status) && expiresAt != null && expiresAt.compareTo(now) <= 0) {
                    continue;
                }

                result.add(HeldSeatDTO.builder()
                        .seatId(doc.getLong("seatId"))
                        .showtimeId(doc.getLong("showtimeId"))
                        .seatCode(doc.getString("seatCode"))
                        .userId(doc.getLong("userId"))
                        .status(status)
                        .holdExpiresAt(expiresAt)
                        .createdAt(doc.getTimestamp("createdAt"))
                        .build());
            }
        } catch (Exception e) {
            log.error("Lỗi lấy danh sách ghế giữ: ", e);
        }
        return result;
    }

    @Scheduled(fixedRate = 30000)
    public void autoReleaseExpiredHolds() {
        if (firestore == null) {
            return;
        }

        try {
            Timestamp now = Timestamp.now();
            ApiFuture<QuerySnapshot> future = firestore.collectionGroup(SUB_COLLECTION_HELD_SEATS)
                    .whereEqualTo("status", "HOLDING")
                    .whereLessThanOrEqualTo("holdExpiresAt", now)
                    .get();

            List<QueryDocumentSnapshot> expiredDocs = future.get().getDocuments();
            if (expiredDocs.isEmpty()) {
                return;
            }

            WriteBatch batch = firestore.batch();
            for (QueryDocumentSnapshot doc : expiredDocs) {
                batch.delete(doc.getReference());
            }
            batch.commit().get();

            log.info("[SeatHoldWorker] Đã tự động dọn dẹp {} ghế hết hạn giữ (TTL 10 phút).", expiredDocs.size());
        } catch (Exception e) {
            log.error("[SeatHoldWorker] Lỗi khi dọn dẹp ghế quá hạn TTL: {}", e.getMessage());
        }
    }

    private void checkFirestoreReady() {
        if (firestore == null) {
            throw new BusinessException("Firestore chưa được cấu hình (thiếu firebase-service-account.json).");
        }
    }
}