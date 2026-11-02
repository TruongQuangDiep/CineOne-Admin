package com.example.cineone_admin.dto.catalog;

import com.example.cineone_admin.entity.Cinema;
import com.example.cineone_admin.entity.Movie;
import com.example.cineone_admin.entity.Room;
import com.example.cineone_admin.entity.Showtime;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowtimeResponse {
    private Long showtimeId;
    private Long movieId;
    private String movieTitle;
    private String moviePosterUrl;
    private Long cinemaId;
    private String cinemaName;
    private Long roomId;
    private String roomName;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "uuuu-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "uuuu-MM-dd'T'HH:mm:ss")
    private LocalDateTime endTime;

    private BigDecimal price;
    private BigDecimal basePrice;
    private String status;

    public static ShowtimeResponse from(Showtime s) {
        Movie m = s.getMovie();
        Room r = s.getRoom();
        Cinema c = r != null ? r.getCinema() : null;
        return ShowtimeResponse.builder()
                .showtimeId(s.getShowtimeId())
                .movieId(m != null ? m.getMovieId() : null)
                .movieTitle(m != null ? m.getTitle() : null)
                .moviePosterUrl(m != null ? m.getPosterUrl() : null)
                .cinemaId(c != null ? c.getCinemaId() : null)
                .cinemaName(c != null ? c.getName() : null)
                .roomId(r != null ? r.getRoomId() : null)
                .roomName(r != null ? r.getName() : null)
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .price(s.getBasePrice())
                .basePrice(s.getBasePrice())
                .status(s.getStatus())
                .build();
    }
}

