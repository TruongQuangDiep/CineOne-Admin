package com.example.cineone_admin.repository;

import com.example.cineone_admin.entity.Payment;
import com.example.cineone_admin.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderCode(String orderCode);

    @Query("SELECT p FROM Payment p WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR LOWER(p.orderCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:status IS NULL OR p.status = :status)")
    Page<Payment> searchPayments(@Param("keyword") String keyword, @Param("status") PaymentStatus status, Pageable pageable);
}