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

    private static final int MAX_SEATS_PER_HOLD = 8;
    private static final int FIRESTORE_MAX_BATCH_SIZE = 500;
    private static final long HOLD_TTL_MINUTES = 10;
    private static final String COLLECTION_SHOWTIMES = "showtimes";
    private static final String SUB_COLLECTION_HELD_SEATS = "held_seats";

    public SeatHoldResponse holdSeats(SeatHoldRequest request) {
        checkFirestoreReady();
        if (request.getSeatIds() == null || request.getSeatIds().isEmpty()) {
            throw new BusinessException("Danh sách ghế không được để trống.");
        }
        if (request.getSeatIds().size() > MAX_SEATS_PER_HOLD) {
            throw new BusinessException("Mỗi lần chỉ được giữ tối đa " + MAX_SEATS_PER_HOLD + " ghế.");
        }
        
        // Kiểm tra phần tử null an toàn, không gây NPE với List.of()
        for (Long seatId : request.getSeatIds()) {
            if (seatId == null) {
                throw new BusinessException("Danh sách ghế không được chứa giá trị rỗng.");
            }
        }

        // Chặn ghế trùng lặp
        if (new HashSet<>(request.getSeatIds()).size() != request.getSeatIds().size()) {
            throw new BusinessException("Danh sách ghế yêu cầu không được chứa ghế trùng lặp.");
        }

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
                // PHA 1: READS (Đọc toàn bộ document ghế trước khi ghi)
                Map<DocumentReference, DocumentSnapshot> snapshots = new HashMap<>();
                for (Long seatId : request.getSeatIds()) {
                    DocumentReference docRef = heldSeatsRef.document(String.valueOf(seatId));
                    DocumentSnapshot snapshot = transaction.get(docRef).get();
                    snapshots.put(docRef, snapshot);
                }

                // Kiểm tra trạng thái và điều kiện xung đột
                for (Map.Entry<DocumentReference, DocumentSnapshot> entry : snapshots.entrySet()) {
                    DocumentSnapshot snap = entry.getValue();
                    if (snap.exists()) {
                        String status = snap.getString("status");
                        Long holdingUserId = snap.contains("userId") ? snap.getLong("userId") : snap.getLong("user_id");
                        Timestamp holdExpiresAt = snap.getTimestamp("holdExpiresAt");
                        String seatCode = snap.getString("seatCode");

                        if ("BOOKED".equalsIgnoreCase(status)) {
                            throw new BusinessException("Ghế " + seatCode + " đã được đặt và thanh toán thành công.");
                        }

                        if ("HOLDING".equalsIgnoreCase(status)) {
                            // Chặn ghi đè khi thiếu hạn giữ chỗ (Fail-closed chống lỗi dữ liệu)
                            if (holdExpiresAt == null) {
                                throw new BusinessException("Ghế " + seatCode + " đang ở trạng thái giữ chỗ không hợp lệ. Vui lòng thử lại sau.");
                            }

                            boolean isExpired = holdExpiresAt.compareTo(nowTimestamp) <= 0;
                            boolean isSameUser = Objects.equals(holdingUserId, request.getUserId());

                            if (!isExpired && !isSameUser) {
                                throw new BusinessException("Ghế " + seatCode + " đang được người khác chọn.");
                            }
                        } else {
                            // Bất kỳ status lạ nào khác (hoặc status null) đều từ chối để an toàn
                            throw new BusinessException("Ghế " + seatCode + " đang có trạng thái không xác định (" + status + ").");
                        }
                    }
                }

                // PHA 2: WRITES (Ghi dữ liệu sau khi toàn bộ ghế hợp lệ)
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
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
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

    public void releaseSeats(Long showtimeId, Long userId, List<Long> seatIds) {
        checkFirestoreReady();
        if (seatIds == null || seatIds.isEmpty()) {
            return;
        }
        if (seatIds.size() > MAX_SEATS_PER_HOLD) {
            throw new BusinessException("Số lượng ghế yêu cầu nhả vượt quá giới hạn (" + MAX_SEATS_PER_HOLD + " ghế).");
        }

        // --- BỔ SUNG: Chặn null và ghế trùng lặp ---
        for (Long seatId : seatIds) {
            if (seatId == null) {
                throw new BusinessException("Danh sách ghế nhả không được chứa giá trị rỗng.");
            }
        }
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new BusinessException("Danh sách ghế nhả không được chứa ghế trùng lặp.");
        }

        try {
            firestore.runTransaction(transaction -> {
                List<DocumentReference> refsToDelete = new ArrayList<>();

                // ==========================================
                // PHA 1: READS & VALIDATION (Đọc toàn bộ trước)
                // ==========================================
                for (Long seatId : seatIds) {
                    DocumentReference seatRef = firestore.collection(COLLECTION_SHOWTIMES)
                            .document(String.valueOf(showtimeId))
                            .collection(SUB_COLLECTION_HELD_SEATS)
                            .document(String.valueOf(seatId));

                    DocumentSnapshot snapshot = transaction.get(seatRef).get();

                    if (snapshot.exists()) {
                        String status = snapshot.getString("status");

                        // 1. Chỉ chấp nhận nhả khi trạng thái chính xác là HOLDING (chặn status null hoặc BOOKED)
                        if (!"HOLDING".equalsIgnoreCase(status)) {
                            throw new BusinessException("Ghế ID " + seatId + " không ở trạng thái giữ chỗ hợp lệ (" + status + "), không thể nhả.");
                        }

                        // 2. Kiểm tra chủ sở hữu: Fail-closed (từ chối xóa nếu thiếu trường user hoặc sai user)
                        Long heldUserId = snapshot.contains("userId") 
                                ? snapshot.getLong("userId") 
                                : snapshot.getLong("user_id");

                        if (heldUserId == null || !heldUserId.equals(userId)) {
                            throw new BusinessException("Ghế ID " + seatId + " không thuộc quyền giữ của bạn.");
                        }

                        refsToDelete.add(seatRef);
                    }
                }

                // ==========================================
                // PHA 2: WRITES / DELETES (Xóa toàn bộ sau)
                // ==========================================
                for (DocumentReference ref : refsToDelete) {
                    transaction.delete(ref);
                }

                return null;
            }).get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof BusinessException be) {
                throw be;
            }
            log.error("Lỗi khi giải phóng ghế: ", e);
            throw new BusinessException("Lỗi khi giải phóng ghế: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Xử lý nhả ghế bị gián đoạn.");
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
                Timestamp createdAt = doc.getTimestamp("createdAt");

                // Bỏ qua ghế HOLDING đã hết hạn
                if ("HOLDING".equalsIgnoreCase(status) && expiresAt != null && expiresAt.compareTo(now) <= 0) {
                    continue;
                }

                // Chuyển Timestamp sang String (ISO-8601) an toàn
                String expiresAtStr = (expiresAt != null) ? expiresAt.toDate().toInstant().toString() : null;
                String createdAtStr = (createdAt != null) ? createdAt.toDate().toInstant().toString() : null;

                result.add(HeldSeatDTO.builder()
                        .seatId(doc.getLong("seatId"))
                        .showtimeId(doc.getLong("showtimeId"))
                        .seatCode(doc.getString("seatCode"))
                        .userId(doc.contains("userId") ? doc.getLong("userId") : doc.getLong("user_id"))
                        .status(status)
                        .holdExpiresAt(expiresAtStr) // Đã chuyển thành String
                        .createdAt(createdAtStr)     // Đã chuyển thành String
                        .build());
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Lỗi lấy danh sách ghế giữ cho suất chiếu {}: ", showtimeId, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException("Không thể tải danh sách ghế giữ từ Firestore. Vui lòng thử lại sau.");
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

            // Chia nhỏ thành các batch tối đa 500 thao tác theo giới hạn của Firestore
            for (int i = 0; i < expiredDocs.size(); i += FIRESTORE_MAX_BATCH_SIZE) {
                WriteBatch batch = firestore.batch();
                List<QueryDocumentSnapshot> chunk = expiredDocs.subList(i, Math.min(i + FIRESTORE_MAX_BATCH_SIZE, expiredDocs.size()));
                for (QueryDocumentSnapshot doc : chunk) {
                    batch.delete(doc.getReference());
                }
                batch.commit().get();
            }

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