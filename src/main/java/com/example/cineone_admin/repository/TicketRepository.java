package com.example.cineone_admin.repository;

import com.example.cineone_admin.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByBooking_User_UserIdOrderByShowtimeStartAsc(Long userId);

    Optional<Ticket> findByTicketIdAndBooking_User_UserId(Long ticketId, Long userId);

    Optional<Ticket> findFirstByTicketCodeIgnoreCase(String ticketCode);

    @Query("SELECT COUNT(t) > 0 FROM Ticket t " +
            "WHERE t.booking.user.userId = :userId " +
            "AND t.booking.showtime.movie.movieId = :movieId " +
            "AND UPPER(t.status) IN ('VALID', 'USED') " +
            "AND ((t.showtimeStart IS NOT NULL AND t.showtimeStart < :now) " +
            "     OR (t.booking.showtime.endTime IS NOT NULL AND t.booking.showtime.endTime < :now))")
    boolean existsWatchedMovieTicket(@Param("userId") Long userId,
                                     @Param("movieId") Long movieId,
                                     @Param("now") LocalDateTime now);
}
