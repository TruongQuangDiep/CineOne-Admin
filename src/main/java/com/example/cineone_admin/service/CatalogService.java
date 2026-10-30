package com.example.cineone_admin.service;

import com.example.cineone_admin.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {
    private final EntityManager em;
    private static final int PAGE_SIZE = 10;

    private static String visible(String type, String id) {
        String match = "m.entityType = '" + type + "' and m.internalId = " + id;
        return " (not exists (select m.mappingId from ProviderMapping m where " + match + ")"
                + " or exists (select m.mappingId from ProviderMapping m where " + match
                + " and m.provider.status <> 'INACTIVE' and m.status = 'ACTIVE')) ";
    }

    public Page<MovieRow> movies(String q, String status, Long genreId, int page) {
        validateStatus(status, "COMING_SOON", "SNEAK_SHOW", "NOW_SHOWING", "END");
        var params = new HashMap<String, Object>();
        String where = " where " + visible("MOVIE", "x.movieId") + search("x.title", "q", q, params)
                + equals("x.status", "status", status, params);
        if (genreId != null) {
            params.put("genreId", genreId);
            where += " and exists (select g.id from MovieGenre g where g.movie = x and g.genre.genreId = :genreId)";
        }
        Page<Movie> result = page(Movie.class, "select x from Movie x", "select count(x) from Movie x",
                where, "x.title, x.movieId", params, page);
        var genres = genresFor(result.getContent().stream().map(Movie::getMovieId).toList());
        return result.map(m -> new MovieRow(m, genres.getOrDefault(m.getMovieId(), "—")));
    }

    public MovieRow movie(Long id) {
        Movie movie = one(Movie.class, "select x from Movie x where x.movieId = :id and "
                + visible("MOVIE", "x.movieId"), id);
        return new MovieRow(movie, genresFor(List.of(id)).getOrDefault(id, "—"));
    }

    public List<Genre> genres() { return em.createQuery("select g from Genre g order by g.name", Genre.class).getResultList(); }

    public List<DataProvider> providers() {
        return em.createQuery("select p from DataProvider p where p.providerType = 'CINEMA'"
                + " and p.status <> 'INACTIVE' order by p.name", DataProvider.class).getResultList();
    }

    public Page<CinemaRow> cinemas(String q, Long providerId, String status, int page) {
        validateStatus(status, "ACTIVE", "INACTIVE");
        var params = new HashMap<String, Object>();
        String where = " where " + visible("CINEMA", "x.cinemaId") + search("x.name", "q", q, params)
                + equals("x.status", "status", status, params);
        if (providerId != null) {
            params.put("providerId", providerId);
            where += " and exists (select m.mappingId from ProviderMapping m where m.entityType = 'CINEMA'"
                    + " and m.internalId = x.cinemaId and m.provider.providerId = :providerId"
                    + " and m.provider.status <> 'INACTIVE' and m.status = 'ACTIVE')";
        }
        Page<Cinema> result = page(Cinema.class, "select x from Cinema x join fetch x.brand",
                "select count(x) from Cinema x", where, "x.name, x.cinemaId", params, page);
        Map<Long, Long> counts = counts("select r.cinema.cinemaId, count(r) from Room r where r.cinema.cinemaId in :ids and "
                + visible("ROOM", "r.roomId") + " group by r.cinema.cinemaId",
                result.stream().map(Cinema::getCinemaId).toList());
        return result.map(c -> new CinemaRow(c, counts.getOrDefault(c.getCinemaId(), 0L)));
    }

    public Cinema cinema(Long id) {
        return one(Cinema.class, "select x from Cinema x join fetch x.brand where x.cinemaId = :id and "
                + visible("CINEMA", "x.cinemaId"), id);
    }

    public Page<RoomRow> rooms(Long cinemaId, int page) {
        cinema(cinemaId);
        Page<Room> result = page(Room.class, "select x from Room x", "select count(x) from Room x",
                " where x.cinema.cinemaId = :cinemaId and " + visible("ROOM", "x.roomId"),
                "x.name, x.roomId", Map.of("cinemaId", cinemaId), page);
        Map<Long, Long> counts = counts("select s.room.roomId, count(s) from Seat s where s.room.roomId in :ids and "
                + visible("SEAT", "s.seatId") + " group by s.room.roomId", result.stream().map(Room::getRoomId).toList());
        return result.map(r -> new RoomRow(r, counts.getOrDefault(r.getRoomId(), 0L)));
    }

    public RoomDetail room(Long cinemaId, Long roomId) {
        Cinema cinema = cinema(cinemaId);
        Room room = one(Room.class, "select x from Room x where x.roomId = :id and "
                + visible("ROOM", "x.roomId"), roomId);
        if (!room.getCinema().getCinemaId().equals(cinemaId)) throw missing();
        List<Seat> seats = em.createQuery("select s from Seat s where s.room.roomId = :id and "
                + visible("SEAT", "s.seatId") + " order by length(s.seatRow), s.seatRow, s.seatNumber", Seat.class)
                .setParameter("id", roomId).getResultList();
        Map<String, List<Seat>> rows = new LinkedHashMap<>();
        for (Seat seat : seats) rows.computeIfAbsent(seat.getSeatRow(), ignored -> new ArrayList<>()).add(seat);
        int columns = seats.stream().mapToInt(Seat::getSeatNumber).max().orElse(0);
        return new RoomDetail(cinema, room, rows, seats.size(), columns);
    }

    public Page<Showtime> showtimes(String cinema, String movie, LocalDate date, String status, int page) {
        validateStatus(status, "OPEN", "CLOSED", "CANCELLED", "ENDED");
        var params = new HashMap<String, Object>();
        String where = " where " + visible("SHOWTIME", "x.showtimeId")
                + " and " + visible("CINEMA", "x.room.cinema.cinemaId")
                + " and " + visible("ROOM", "x.room.roomId")
                + " and " + visible("MOVIE", "x.movie.movieId")
                + search("x.room.cinema.name", "cinema", cinema, params)
                + search("x.movie.title", "movie", movie, params) + equals("x.status", "status", status, params);
        if (date != null) {
            params.put("from", date.atStartOfDay()); params.put("to", date.plusDays(1).atStartOfDay());
            where += " and x.startTime >= :from and x.startTime < :to";
        }
        return page(Showtime.class, "select x from Showtime x join fetch x.movie join fetch x.room r"
                        + " join fetch r.cinema c join fetch c.brand", "select count(x) from Showtime x",
                where, "x.startTime desc, x.showtimeId desc", params, page);
    }

    public Page<FnbItem> fnb(String q, String cinema, Long cinemaId, String status, int page) {
        validateStatus(status, "ACTIVE", "INACTIVE", "OUT_OF_STOCK");
        var params = new HashMap<String, Object>();
        String where = " where " + visible("FNB_ITEM", "x.fnbId") + " and " + visible("CINEMA", "x.cinema.cinemaId")
                + search("x.name", "q", q, params) + search("x.cinema.name", "cinema", cinema, params)
                + equals("x.status", "status", status, params);
        if (cinemaId != null) {
            cinema(cinemaId); params.put("cinemaId", cinemaId); where += " and x.cinema.cinemaId = :cinemaId";
        }
        return page(FnbItem.class, "select x from FnbItem x join fetch x.cinema c join fetch c.brand join fetch x.brand",
                "select count(x) from FnbItem x", where, "x.cinema.name, x.name, x.fnbId", params, page);
    }

    private Map<Long, String> genresFor(List<Long> ids) {
        Map<Long, List<String>> result = new HashMap<>();
        if (!ids.isEmpty()) {
            List<MovieGenre> links = em.createQuery("select g from MovieGenre g join fetch g.genre where g.movie.movieId in :ids"
                    + " order by g.genre.name", MovieGenre.class).setParameter("ids", ids).getResultList();
            for (MovieGenre link : links) result.computeIfAbsent(link.getMovie().getMovieId(), ignored -> new ArrayList<>())
                    .add(link.getGenre().getName());
        }
        Map<Long, String> names = new HashMap<>();
        result.forEach((id, values) -> names.put(id, String.join(", ", values)));
        return names;
    }

    private Map<Long, Long> counts(String jpql, List<Long> ids) {
        Map<Long, Long> result = new HashMap<>();
        if (!ids.isEmpty()) for (Object[] row : em.createQuery(jpql, Object[].class).setParameter("ids", ids).getResultList())
            result.put((Long) row[0], (Long) row[1]);
        return result;
    }

    private <T> Page<T> page(Class<T> type, String select, String count, String where, String order,
                              Map<String, ?> params, int requestedPage) {
        int number = Math.max(0, Math.min(requestedPage, 100_000));
        TypedQuery<T> query = em.createQuery(select + where + " order by " + order, type);
        TypedQuery<Long> total = em.createQuery(count + where, Long.class);
        params.forEach((key, value) -> { query.setParameter(key, value); total.setParameter(key, value); });
        return new PageImpl<>(query.setFirstResult(number * PAGE_SIZE).setMaxResults(PAGE_SIZE).getResultList(),
                PageRequest.of(number, PAGE_SIZE), total.getSingleResult());
    }

    private <T> T one(Class<T> type, String query, Long id) {
        return em.createQuery(query, type).setParameter("id", id).getResultStream().findFirst().orElseThrow(CatalogService::missing);
    }
    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu hoặc nhà cung cấp đã ngừng đồng bộ.");
    }
    private static void validateStatus(String status, String... allowed) {
        if (status != null && !status.isBlank() && !List.of(allowed).contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ.");
    }
    private static String search(String field, String key, String value, Map<String, Object> params) {
        if (value == null || value.isBlank()) return "";
        String term = value.strip();
        if (term.length() > 255) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Từ khóa tối đa 255 ký tự.");
        params.put(key, "%" + term.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        return " and lower(" + field + ") like :" + key + " escape '\\'";
    }
    private static String equals(String field, String key, String value, Map<String, Object> params) {
        if (value == null || value.isBlank()) return "";
        params.put(key, value); return " and " + field + " = :" + key;
    }
    public record MovieRow(Movie movie, String genres) {}
    public record CinemaRow(Cinema cinema, long roomCount) {}
    public record RoomRow(Room room, long seatCount) {}
    public record RoomDetail(Cinema cinema, Room room, Map<String, List<Seat>> rows, int seatCount, int columns) {}
}
