package com.example.cineone_admin.repository;

import com.example.cineone_admin.entity.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {
    List<BookingSeat> findByBooking_BookingId(Long bookingId);
}