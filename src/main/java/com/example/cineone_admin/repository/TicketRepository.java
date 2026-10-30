package com.example.cineone_admin.repository;

import com.example.cineone_admin.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByBooking_User_UserIdOrderByShowtimeStartAsc(Long userId);

    Optional<Ticket> findByTicketIdAndBooking_User_UserId(Long ticketId, Long userId);
}

