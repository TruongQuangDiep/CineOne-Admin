package com.example.cineone_admin.tmdb;

import com.example.cineone_admin.entity.*;
import com.example.cineone_admin.enums.ProviderEntityType;
import com.example.cineone_admin.repository.*;
import com.example.cineone_admin.service.ProviderMappingService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Một phim, genres và toàn bộ mappings được lưu trong cùng giao dịch. Không gọi HTTP ở đây. */
@Service
public class TmdbMovieStore {
    private final EntityManager em;
    private final ProviderMappingService mappings;
    private final MovieRepository movies;
    private final GenreRepository genres;
    private final MovieGenreRepository links;

    public TmdbMovieStore(EntityManager em, ProviderMappingService mappings, MovieRepository movies,
            GenreRepository genres, MovieGenreRepository links) {
        this.em = em;
        this.mappings = mappings;
        this.movies = movies;
        this.genres = genres;
        this.links = links;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long save(TmdbMovieAdapter.EnrichedMovie enriched) {
        Objects.requireNonNull(enriched);
        // Khóa theo provider để hai lần nhập TMDB song song không tạo trùng phim/thể loại.
        var providers = em.createQuery("select p from DataProvider p where p.code = :code", DataProvider.class)
                .setParameter("code", "TMDB").setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        if (providers.size() != 1 || !"ACTIVE".equals(providers.getFirst().getStatus())
                || Boolean.TRUE.equals(providers.getFirst().getMock()))
            throw new IllegalStateException("Cần DataProvider TMDB thật ở trạng thái ACTIVE trước khi lưu");
        Long providerId = providers.getFirst().getProviderId();
        var source = enriched.movie();
        Long movieId = mappings.resolveOrCreate(providerId, ProviderEntityType.MOVIE, source.externalId(), () -> {
            var movie = new Movie();
            movie.setTitle(source.title());
            movie.setDuration(source.duration());
            return movie;
        });
        var movie = movies.findById(movieId).orElseThrow();
        // Giữ metadata đã có, bao gồm chỉnh sửa thủ công của quản trị viên.
        if (blank(movie.getTitle())) movie.setTitle(source.title());
        if (blank(movie.getDescription())) movie.setDescription(source.description());
        if (movie.getDuration() == null || movie.getDuration() <= 0) movie.setDuration(source.duration());
        if (blank(movie.getCountry())) movie.setCountry(source.country());
        if (movie.getReleaseDate() == null) movie.setReleaseDate(source.releaseDate());
        if (blank(movie.getPosterUrl())) movie.setPosterUrl(source.posterUrl());
        if (blank(movie.getTrailerUrl())) movie.setTrailerUrl(source.trailerUrl());
        if (blank(movie.getDirector())) movie.setDirector(source.director());
        if (blank(movie.getCastInfo())) movie.setCastInfo(source.castInfo());

        for (var incoming : enriched.genres()) {
            String externalId = incoming.id().toString();
            Long genreId = mappings.findInternalId(providerId, ProviderEntityType.GENRE, externalId).orElse(null);
            if (genreId == null) {
                var genre = genres.findByName(incoming.name()).orElseGet(() -> {
                    var created = new Genre();
                    created.setName(incoming.name());
                    return genres.save(created);
                });
                genreId = genre.getGenreId();
            }
            // bind kiểm tra cả chiều ngược, không nhập nhằng hai TMDB ID thành cùng một thể loại.
            mappings.bind(providerId, ProviderEntityType.GENRE, externalId, genreId);
            var key = new MovieGenreId();
            key.setMovieId(movieId);
            key.setGenreId(genreId);
            if (!links.existsById(key)) {
                var link = new MovieGenre();
                link.setId(key);
                link.setMovie(movie);
                link.setGenre(em.getReference(Genre.class, genreId));
                em.persist(link);
            }
        }
        mappings.bind(providerId, ProviderEntityType.MOVIE, source.externalId(), movieId);
        em.flush();
        return movieId;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
