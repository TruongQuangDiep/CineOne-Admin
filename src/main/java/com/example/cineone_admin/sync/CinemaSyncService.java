package com.example.cineone_admin.sync;

import com.example.cineone_admin.dto.sync.*;
import com.example.cineone_admin.dto.sync.NormalizedCatalogDTO.*;
import com.example.cineone_admin.enums.ProviderEntityType;
import com.example.cineone_admin.repository.DataProviderRepository;
import com.example.cineone_admin.service.ProviderMappingService;
import com.example.cineone_admin.tmdb.TmdbMovieService;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.example.cineone_admin.enums.ProviderEntityType.*;

@Service
public class CinemaSyncService {
    private final CinemaSyncAdapterFactory factory;
    private final DataProviderRepository providers;
    private final ProviderMappingService mappings;
    private final TmdbMovieService tmdb;
    private final SyncRecordStore store;
    private final SyncLogService logs;
    public CinemaSyncService(CinemaSyncAdapterFactory factory, DataProviderRepository providers,
            ProviderMappingService mappings, TmdbMovieService tmdb, SyncRecordStore store, SyncLogService logs) {
        this.factory = factory; this.providers = providers; this.mappings = mappings;
        this.tmdb = tmdb; this.store = store; this.logs = logs;
    }

    /** No enclosing transaction: catching an error must never poison later records. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SyncRunResult sync(String providerCode, SyncRequestDTO request) {
        Objects.requireNonNull(request, "Sync request is required");
        var provider = providers.findByCode(providerCode).orElseThrow(
                () -> new IllegalArgumentException("Nhà cung cấp chưa được cấu hình: " + providerCode));
        Long logId = logs.start(provider.getProviderId()); // Fail before writes if logging is unavailable.
        long started = System.nanoTime();
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        var run = new Run(provider.getProviderId(), providerCode);
        try {
            if (!"ACTIVE".equals(provider.getStatus())) throw new IllegalArgumentException("Nhà cung cấp không ACTIVE");
            var read = factory.getAdapter(providerCode).sync(request, date);
            if (!providerCode.equals(read.providerCode())) throw new IllegalArgumentException("Adapter trả về sai mã nguồn");
            for (var error : read.errors()) run.fail(error.entityType(), error.externalId(), error.message());
            var data = read.data();
            run.save(data.cinemaBrands(), CINEMA_BRAND, CinemaBrandDTO::externalId);
            run.save(data.cinemas(), CINEMA, CinemaDTO::externalId);
            run.save(data.rooms(), ROOM, RoomDTO::externalId);
            run.save(data.seats(), SEAT, SeatDTO::externalId);
            run.save(data.fnbItems(), FNB_ITEM, FnbDTO::externalId);
            // Movies are imported once per distinct reference, before any showtime is written.
            Map<ProviderReferenceDTO, String> movieFailures = new HashMap<>();
            for (var movie : data.showtimes().stream().map(ShowtimeDTO::movie).distinct().toList()) {
                try { ensureMovie(movie); }
                catch (RuntimeException ex) { movieFailures.put(movie, reason(ex)); }
            }
            for (var show : data.showtimes()) {
                if (movieFailures.containsKey(show.movie())) {
                    run.fail(SHOWTIME, show.externalId(), "Không nhập được phim " + show.movie().externalId()
                            + ": " + movieFailures.get(show.movie()));
                } else run.save(List.of(show), SHOWTIME, ShowtimeDTO::externalId);
            }
            for (var movie : data.movies()) run.fail(MOVIE, movie.externalId(), "Nhập phim qua TMDB, không qua nguồn hãng rạp");
        } catch (RuntimeException ex) {
            run.fail(null, null, reason(ex));
        }
        var result = new SyncRunResult(logId, providerCode, run.errors.isEmpty() ? "SUCCESS" : "FAILED", date,
                run.success + run.errors.size(), run.success, run.errors.size(),
                (System.nanoTime() - started) / 1_000_000, run.errors);
        logs.finish(result); // Propagate log failure; never claim a finished run when log persistence failed.
        return result;
    }

    private void ensureMovie(ProviderReferenceDTO movie) {
        var provider = providers.findByCode(movie.providerCode()).orElseThrow(
                () -> new IllegalArgumentException("Chưa cấu hình nguồn phim " + movie.providerCode()));
        if (!"ACTIVE".equals(provider.getStatus())) throw new IllegalArgumentException("Nguồn phim không ACTIVE");
        if (mappings.findInternalId(provider.getProviderId(), MOVIE, movie.externalId()).isPresent()) return;
        if (!"TMDB".equals(movie.providerCode()) || Boolean.TRUE.equals(provider.getMock()))
            throw new IllegalArgumentException("Chỉ tự nhập phim từ nguồn TMDB thật");
        long tmdbId;
        try { tmdbId = Long.parseLong(movie.externalId()); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("TMDB ID không hợp lệ"); }
        if (tmdbId <= 0) throw new IllegalArgumentException("TMDB ID phải dương");
        tmdb.importMovie(tmdbId);
    }

    private class Run {
        final Long providerId;
        final String providerCode;
        int success;
        final List<SyncRunResult.Error> errors = new ArrayList<>();
        final Set<ProviderReferenceDTO> failedRefs = new HashSet<>();
        Run(Long id, String code) { providerId = id; providerCode = code; }
        void fail(ProviderEntityType type, String id, String message) {
            errors.add(new SyncRunResult.Error(type, id, type == SHOWTIME ? id : null, message));
            if (type != null && id != null && !id.isBlank() && id.length() <= 255)
                failedRefs.add(new ProviderReferenceDTO(providerCode, type, id));
        }
        <T> void save(List<T> records, ProviderEntityType type, Function<T, String> id) {
            for (T source : records) {
                try {
                    if (references(source).stream().anyMatch(failedRefs::contains))
                        throw new IllegalArgumentException("Bản ghi cha lỗi trong đợt này; không sử dụng lại dữ liệu cha cũ");
                    store.save(providerId, type, id.apply(source), source);
                    success++; // REQUIRES_NEW has committed before this point.
                } catch (RuntimeException ex) { fail(type, id.apply(source), reason(ex)); }
            }
        }
    }

    private static List<ProviderReferenceDTO> references(Object source) {
        return switch (source) {
            case CinemaDTO s -> List.of(s.brand()); case RoomDTO s -> List.of(s.cinema());
            case SeatDTO s -> List.of(s.room()); case FnbDTO s -> List.of(s.brand(), s.cinema());
            case ShowtimeDTO s -> List.of(s.movie(), s.room()); default -> List.of();
        };
    }

    static String reason(RuntimeException ex) {
        // JDBC/HTTP messages may include SQL, credentials or source paths. Expose only business errors.
        if (ex instanceof IllegalArgumentException && ex.getMessage() != null) {
            String message = ex.getMessage();
            return message.length() > 1000 ? message.substring(0, 1000) : message;
        }
        return "Không xử lý được bản ghi (" + ex.getClass().getSimpleName() + ")";
    }
}
