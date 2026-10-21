package com.example.cineone_admin.sync.mock;

import com.example.cineone_admin.dto.sync.*;
import com.example.cineone_admin.dto.sync.NormalizedCatalogDTO.*;
import com.example.cineone_admin.enums.*;
import com.example.cineone_admin.model.RoomType;
import com.example.cineone_admin.sync.CinemaSyncAdapter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static com.example.cineone_admin.enums.ProviderEntityType.*;

public final class MockCinemaAdapter implements CinemaSyncAdapter {
    private static final Set<ProviderEntityType> SUPPORTED = Set.of(CINEMA_BRAND, CINEMA, ROOM, SEAT, SHOWTIME, FNB_ITEM);
    private final String code;
    private final String name;
    private final boolean json;
    private final MockSourceReader reader;
    private final Clock clock;

    public MockCinemaAdapter(String code, String name, boolean json, MockSourceReader reader, Clock clock) {
        this.code = Objects.requireNonNull(code);
        this.name = Objects.requireNonNull(name);
        this.json = json;
        this.reader = Objects.requireNonNull(reader);
        this.clock = Objects.requireNonNull(clock).withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    @Override public String providerCode() { return code; }

    @Override public SyncResultDTO sync(SyncRequestDTO request) {
        return sync(request, LocalDate.now(clock));
    }

    @Override public SyncResultDTO sync(SyncRequestDTO request, LocalDate referenceDate) {
        Objects.requireNonNull(request, "Yêu cầu đồng bộ là bắt buộc");
        if (request.mode() != SyncMode.FULL)
            return failure("Mock không có updated_at; chỉ hỗ trợ FULL, không hỗ trợ INCREMENTAL");
        if (!SUPPORTED.containsAll(request.entityTypes()))
            return failure("Nguồn hãng rạp không cung cấp MOVIE/GENRE; dùng TMDB cho metadata phim");
        try {
            return normalize(request, Objects.requireNonNull(referenceDate));
        } catch (IllegalArgumentException | DateTimeException | IOException ex) {
            return failure(ex.getMessage() == null ? "Nguồn mock không hợp lệ" : ex.getMessage());
        }
    }

    private SyncResultDTO failure(String message) {
        return SyncResultDTO.of(code, NormalizedCatalogDTO.empty(), List.of(new SyncErrorDTO(null, null, message)));
    }

    private SyncResultDTO normalize(SyncRequestDTO request, LocalDate referenceDate) throws IOException {
        Set<ProviderEntityType> scope = request.entityTypes();
        var brand = new CinemaBrandDTO(code, name, null, CinemaBrandStatus.ACTIVE);
        List<CinemaDTO> cinemas = new ArrayList<>();
        List<RoomDTO> rooms = new ArrayList<>();
        List<SeatDTO> seats = new ArrayList<>();
        List<ShowtimeDTO> shows = new ArrayList<>();
        List<FnbDTO> foods = new ArrayList<>();
        List<SyncErrorDTO> errors = new ArrayList<>();
        Set<String> seenCinemas = new HashSet<>();
        if (!scope.equals(Set.of(CINEMA_BRAND))) {
            for (var row : source("cinemas", "cinemas", "external_id", "name", "address", "latitude", "longitude", "phone")) {
                try {
                    require(seenCinemas.add(text(row, "external_id")), "Trùng ID rạp");
                    cinemas.add(new CinemaDTO(text(row, "external_id"), ref(CINEMA_BRAND, code), text(row, "name"),
                            text(row, "address"), coordinate(row, "latitude"), coordinate(row, "longitude"),
                            optional(row, "phone"), CinemaStatus.valueOf(status(row, "ACTIVE"))));
                } catch (IllegalArgumentException | DateTimeException ex) { errors.add(error(CINEMA, row, ex)); }
            }
            unique(cinemas.stream().map(CinemaDTO::externalId).toList(), "cinemas.external_id");
        }
        Set<String> cinemaIds = new HashSet<>(cinemas.stream().map(CinemaDTO::externalId).toList());
        Map<RoomKey, RoomDTO> roomLookup = new HashMap<>();
        if (scope.contains(ROOM) || scope.contains(SEAT) || scope.contains(SHOWTIME)) {
            Map<String, List<Map<String, String>>> templates = new LinkedHashMap<>();
            for (var row : source("seat_templates", "templates", "template_code", "room_type", "seat_row", "seat_number", "seat_type")) {
                templates.computeIfAbsent(text(row, "template_code"), ignored -> new ArrayList<>()).add(row);
            }
            // Validate even unused templates: bad source must not silently pass a full sync.
            for (var entry : templates.entrySet()) {
                List<String> positions = new ArrayList<>();
                String type = text(entry.getValue().getFirst(), "room_type");
                RoomType.of(type);
                for (var row : entry.getValue()) {
                    require(type.equals(text(row, "room_type")), "Một template có nhiều room_type: " + entry.getKey());
                    var seat = seat(row, "template-validation");
                    positions.add(seat.seatRow() + ":" + seat.seatNumber());
                }
                unique(positions, "Ghế trùng trong template " + entry.getKey());
            }
            for (var row : source("rooms", "rooms", "external_id", "cinema_id", "name", "room_type", "template_code")) {
                try {
                    require(rooms.stream().noneMatch(r -> r.externalId().equals(text(row, "external_id"))), "Trùng ID phòng");
                    String cinemaId = text(row, "cinema_id");
                    require(cinemaIds.contains(cinemaId), "rooms tham chiếu rạp không tồn tại: " + cinemaId);
                    String templateId = text(row, "template_code");
                    var template = templates.get(templateId);
                    require(template != null && !template.isEmpty(), "Không tìm thấy mẫu ghế: " + templateId);
                    String type = text(row, "room_type");
                    require(type.equals(text(template.getFirst(), "room_type")), "Loại phòng không khớp mẫu ghế: " + templateId);
                    var room = new RoomDTO(text(row, "external_id"), ref(CINEMA, cinemaId), text(row, "name"),
                            RoomType.of(type), RoomStatus.valueOf(status(row, "ACTIVE")));
                    require(roomLookup.putIfAbsent(new RoomKey(cinemaId, room.name()), room) == null,
                            "Trùng tên phòng trong rạp " + cinemaId + ": " + room.name());
                    rooms.add(room);
                    if (scope.contains(SEAT)) for (var position : template) seats.add(seat(position, room.externalId()));
                } catch (IllegalArgumentException | DateTimeException ex) { errors.add(error(ROOM, row, ex)); }
            }
            unique(rooms.stream().map(RoomDTO::externalId).toList(), "rooms.external_id");
        }
        if (scope.contains(SHOWTIME)) {
            LocalDate today = referenceDate; // Chốt một lần để không lệch ngày giữa các bản ghi.
            List<String> sourceIds = new ArrayList<>();
            for (var row : source("showtimes", "showtimes", "external_id", "cinema_id", "room_name", "room_type",
                    "tmdb_id", "day_offset", "start_time_slot", "duration_minutes", "base_price")) {
                try {
                    require(!sourceIds.contains(text(row, "external_id")), "Trùng ID suất chiếu");
                    sourceIds.add(text(row, "external_id"));
                    var room = roomLookup.get(new RoomKey(text(row, "cinema_id"), text(row, "room_name")));
                    require(room != null, "Suất chiếu tham chiếu phòng không tồn tại: " + text(row, "external_id"));
                    require(room.roomType().value().equals(text(row, "room_type")), "room_type suất chiếu không khớp phòng");
                    int offset = integer(row, "day_offset", 0);
                    int duration = integer(row, "duration_minutes", 1);
                    String movieId = text(row, "tmdb_id");
                    require(movieId.matches("[1-9][0-9]*"), "tmdb_id phải là số nguyên dương");
                    String time = text(row, "start_time_slot");
                    require(time.matches("[0-9]{2}:[0-9]{2}"), "start_time_slot phải theo HH:mm");
                    var start = today.plusDays(offset).atTime(LocalTime.parse(time));
                    // ID gắn ngày: không di chuyển suất đã có đặt vé sang ngày mới khi mock quay vòng.
                    var show = new ShowtimeDTO(composite(text(row, "external_id"), start.toLocalDate().toString()),
                            new ProviderReferenceDTO("TMDB", MOVIE, movieId), ref(ROOM, room.externalId()),
                            start, start.plusMinutes(duration), decimal(row, "base_price"),
                            ShowtimeStatus.valueOf(status(row, "OPEN")));
                    if (request.fromDate() == null || (!start.toLocalDate().isBefore(request.fromDate())
                            && !start.toLocalDate().isAfter(request.toDate()))) shows.add(show);
                } catch (IllegalArgumentException | DateTimeException ex) { errors.add(error(SHOWTIME, row, ex)); }
            }
            unique(sourceIds, "showtimes.external_id");
        }
        if (scope.contains(FNB_ITEM)) {
            List<String> sourceIds = new ArrayList<>();
            for (var row : source("fnb", "items", "external_id", "name", "price", "description", "image_url")) {
                try {
                    String id = text(row, "external_id");
                    require(!sourceIds.contains(id), "Trùng ID F&B");
                    sourceIds.add(id);
                    String description = optional(row, "description");
                    require(description == null || description.getBytes(StandardCharsets.UTF_8).length <= 65535,
                            "Mô tả F&B vượt giới hạn TEXT");
                    for (var cinema : cinemas) foods.add(new FnbDTO(composite(id, cinema.externalId()),
                            ref(CINEMA_BRAND, code), ref(CINEMA, cinema.externalId()), text(row, "name"), description,
                            decimal(row, "price"), optional(row, "image_url"), FnbStatus.valueOf(status(row, "ACTIVE"))));
                } catch (IllegalArgumentException | DateTimeException ex) { errors.add(error(FNB_ITEM, row, ex)); }
            }
            unique(sourceIds, "fnb.external_id");
        }
        var catalog = new NormalizedCatalogDTO(scope.contains(CINEMA) ? cinemas : List.of(),
                scope.contains(ROOM) ? rooms : List.of(), seats, shows, foods, List.of(),
                scope.contains(CINEMA_BRAND) ? List.of(brand) : List.of());
        return SyncResultDTO.of(code, catalog, errors);
    }

    private SyncErrorDTO error(ProviderEntityType type, Map<String, String> row, RuntimeException ex) {
        String message = ex.getMessage() == null ? "Dữ liệu nguồn không hợp lệ" : ex.getMessage();
        return new SyncErrorDTO(type, optional(row, "external_id"), message);
    }

    private SeatDTO seat(Map<String, String> row, String roomId) {
        int number = integer(row, "seat_number", 1);
        String seatRow = text(row, "seat_row");
        return new SeatDTO(composite(roomId, seatRow + ":" + number), ref(ROOM, roomId), seatRow, number,
                SeatType.valueOf(text(row, "seat_type")), SeatStatus.ACTIVE);
    }

    private List<Map<String, String>> source(String file, String array, String... fields) throws IOException {
        try {
            return reader.read(code, json, file, array, Set.of(fields));
        } catch (RuntimeException | IOException ex) {
            // Không lộ đường dẫn máy chủ hoặc toàn bộ nội dung nguồn trong lỗi trả về.
            throw new IOException("Không đọc được " + code + "/" + file + ": " + ex.getClass().getSimpleName(), ex);
        }
    }

    private ProviderReferenceDTO ref(ProviderEntityType type, String id) { return new ProviderReferenceDTO(code, type, id); }
    private record RoomKey(String cinemaId, String name) {}
    private static String text(Map<String, String> row, String field) {
        if (row.containsKey("__record_error")) throw new IllegalArgumentException(row.get("__record_error"));
        String value = optional(row, field);
        require(value != null, "Trường bắt buộc bị trống hoặc sai kiểu: " + field);
        return value;
    }
    private static String optional(Map<String, String> row, String field) {
        String value = row.get(field);
        return value == null || value.isBlank() ? null : value;
    }
    private static String status(Map<String, String> row, String fallback) {
        return row.containsKey("status") ? text(row, "status") : fallback;
    }
    private static BigDecimal decimal(Map<String, String> row, String field) {
        try { return new BigDecimal(text(row, field)); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Số thập phân không hợp lệ: " + field); }
    }
    private static BigDecimal coordinate(Map<String, String> row, String field) {
        return optional(row, field) == null ? null : decimal(row, field);
    }
    private static int integer(Map<String, String> row, String field, int minimum) {
        try {
            String value = text(row, field);
            require(value.matches("[0-9]+"), "Số nguyên không hợp lệ: " + field);
            int number = Integer.parseInt(value);
            require(number >= minimum, "Giá trị ngoài phạm vi: " + field);
            return number;
        } catch (NumberFormatException ex) { throw new IllegalArgumentException("Số nguyên vượt phạm vi: " + field); }
    }
    private static String composite(String left, String right) {
        return escape(left) + "::" + escape(right);
    }
    private static String escape(String value) { return value.replace("%", "%25").replace(":", "%3A"); }
    private static void unique(List<String> ids, String field) {
        require(new HashSet<>(ids).size() == ids.size(), "Trùng định danh: " + field);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
