package com.example.cineone_admin.dto.sync;

import com.example.cineone_admin.model.RoomType;

import com.example.cineone_admin.enums.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Dữ liệu chuẩn hóa dùng chung để lưu trữ. ID ở đây là ID bên ngoài; ID nội bộ do cơ sở dữ liệu sinh. */
public record NormalizedCatalogDTO(
        List<CinemaDTO> cinemas, List<RoomDTO> rooms, List<SeatDTO> seats,
        List<ShowtimeDTO> showtimes, List<FnbDTO> fnbItems,
        List<MovieDTO> movies, List<CinemaBrandDTO> cinemaBrands) {
    public NormalizedCatalogDTO {
        movies = List.copyOf(movies);
        cinemaBrands = List.copyOf(cinemaBrands);
        cinemas = List.copyOf(cinemas);
        rooms = List.copyOf(rooms);
        seats = List.copyOf(seats);
        showtimes = List.copyOf(showtimes);
        fnbItems = List.copyOf(fnbItems);
    }

    /** Giữ tương thích cho bên gọi chỉ cung cấp năm nhóm dữ liệu rạp ban đầu. */
    public NormalizedCatalogDTO(List<CinemaDTO> cinemas, List<RoomDTO> rooms, List<SeatDTO> seats,
            List<ShowtimeDTO> showtimes, List<FnbDTO> fnbItems) {
        this(cinemas, rooms, seats, showtimes, fnbItems, List.of(), List.of());
    }

    public static NormalizedCatalogDTO empty() {
        return new NormalizedCatalogDTO(List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public int size() {
        return cinemas.size() + rooms.size() + seats.size() + showtimes.size() + fnbItems.size()
                + movies.size() + cinemaBrands.size();
    }

    private static void id(String id) {
        if (id == null || id.isBlank() || id.length() > 255)
            throw new IllegalArgumentException("ID bên ngoài là bắt buộc và không được vượt quá 255 ký tự");
    }

    private static void reference(ProviderReferenceDTO ref, ProviderEntityType expected) {
        Objects.requireNonNull(ref, "Tham chiếu là bắt buộc");
        if (ref.entityType() != expected)
            throw new IllegalArgumentException("Loại thực thể được tham chiếu phải là " + expected);
    }

    private static void text(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max)
            throw new IllegalArgumentException(field + " là bắt buộc và không được vượt quá " + max + " ký tự");
    }

    private static void optionalText(String value, String field, int max) {
        if (value != null && value.length() > max)
            throw new IllegalArgumentException(field + " không được vượt quá " + max + " ký tự");
    }

    private static void money(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.stripTrailingZeros().scale() > 2
                || value.compareTo(new BigDecimal("9999999999.99")) > 0)
            throw new IllegalArgumentException("Giá phải không âm và nằm trong giới hạn DECIMAL(12,2)");
    }

    private static void coordinate(BigDecimal value, int limit) {
        if (value != null && value.abs().compareTo(BigDecimal.valueOf(limit)) > 0)
            throw new IllegalArgumentException("Tọa độ nằm ngoài phạm vi hợp lệ");
    }

    public record CinemaDTO(String externalId, ProviderReferenceDTO brand, String name,
            String address, BigDecimal latitude, BigDecimal longitude, String phone, CinemaStatus status) {
        public CinemaDTO {
            id(externalId);
            reference(brand, ProviderEntityType.CINEMA_BRAND);
            text(name, "Tên", 150);
            text(address, "Địa chỉ", 255);
            coordinate(latitude, 90);
            coordinate(longitude, 180);
            optionalText(phone, "Số điện thoại", 20);
            Objects.requireNonNull(status, "Trạng thái là bắt buộc");
        }
    }

    public record RoomDTO(String externalId, ProviderReferenceDTO cinema, String name,
            RoomType roomType, RoomStatus status) {
        public RoomDTO {
            id(externalId);
            reference(cinema, ProviderEntityType.CINEMA);
            text(name, "Tên", 100);
            Objects.requireNonNull(roomType, "Loại phòng là bắt buộc");
            Objects.requireNonNull(status, "Trạng thái là bắt buộc");
        }
    }

    public record SeatDTO(String externalId, ProviderReferenceDTO room, String seatRow,
            Integer seatNumber, SeatType seatType, SeatStatus status) {
        public SeatDTO {
            id(externalId);
            reference(room, ProviderEntityType.ROOM);
            text(seatRow, "Hàng ghế", 10);
            if (seatNumber == null || seatNumber <= 0)
                throw new IllegalArgumentException("Số ghế phải là số nguyên dương");
            Objects.requireNonNull(seatType, "Loại ghế là bắt buộc");
            Objects.requireNonNull(status, "Trạng thái ghế là bắt buộc");
        }
    }

    public record ShowtimeDTO(String externalId, ProviderReferenceDTO movie, ProviderReferenceDTO room,
            LocalDateTime startTime, LocalDateTime endTime, BigDecimal basePrice, ShowtimeStatus status) {
        public ShowtimeDTO {
            id(externalId);
            reference(movie, ProviderEntityType.MOVIE);
            reference(room, ProviderEntityType.ROOM);
            Objects.requireNonNull(startTime, "Thời gian bắt đầu là bắt buộc");
            Objects.requireNonNull(endTime, "Thời gian kết thúc là bắt buộc");
            if (!endTime.isAfter(startTime))
                throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu");
            money(basePrice);
            Objects.requireNonNull(status, "Trạng thái là bắt buộc");
        }
    }

    public record FnbDTO(String externalId, ProviderReferenceDTO brand, ProviderReferenceDTO cinema,
            String name, String description, BigDecimal price, String imageUrl, FnbStatus status) {
        public FnbDTO {
            id(externalId);
            reference(brand, ProviderEntityType.CINEMA_BRAND);
            reference(cinema, ProviderEntityType.CINEMA);
            text(name, "Tên", 150);
            money(price);
            optionalText(imageUrl, "Đường dẫn hình ảnh", 500);
            Objects.requireNonNull(status, "Trạng thái là bắt buộc");
        }
    }
    public record CinemaBrandDTO(String externalId, String name, String logoUrl, CinemaBrandStatus status) {
        public CinemaBrandDTO {
            id(externalId);
            text(name, "Tên hãng rạp", 150);
            optionalText(logoUrl, "Đường dẫn logo", 500);
            Objects.requireNonNull(status, "Trạng thái hãng rạp là bắt buộc");
        }
    }

    public record MovieDTO(String externalId, String title, String description, Integer duration,
            String ageRating, String country, LocalDate releaseDate, String posterUrl,
            String trailerUrl, String director, String castInfo, MovieStatus status) {
        public MovieDTO {
            id(externalId);
            text(title, "Tên phim", 255);
            if (duration == null || duration <= 0)
                throw new IllegalArgumentException("Thời lượng phim phải là số nguyên dương");
            optionalText(ageRating, "Phân loại độ tuổi", 20);
            optionalText(country, "Quốc gia", 100);
            optionalText(posterUrl, "Đường dẫn poster", 500);
            optionalText(trailerUrl, "Đường dẫn trailer", 500);
            optionalText(director, "Đạo diễn", 255);
            Objects.requireNonNull(status, "Trạng thái phim là bắt buộc");
        }
    }
}
