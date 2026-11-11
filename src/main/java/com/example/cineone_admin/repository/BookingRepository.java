package com.example.cineone_admin.repository;

import com.example.cineone_admin.entity.Booking;
import com.example.cineone_admin.enums.BookingStatus;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("SELECT b FROM Booking b WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR LOWER(b.bookingCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:status IS NULL OR b.status = :status)")
    Page<Booking> searchBookings(@Param("keyword") String keyword, @Param("status") BookingStatus status, Pageable pageable);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Booking b WHERE b.user.userId = :userId AND b.status = com.example.cineone_admin.enums.BookingStatus.CONFIRMED")
    BigDecimal calculateTotalSpendingByUserId(@Param("userId") Long userId);

    java.util.Optional<Booking> findByBookingCodeIgnoreCase(String bookingCode);
}