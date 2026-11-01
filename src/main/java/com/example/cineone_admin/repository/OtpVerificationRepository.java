package com.example.cineone_admin.repository;

import com.example.cineone_admin.entity.OtpVerification;
import com.example.cineone_admin.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    Optional<OtpVerification> findTopByUserAndTypeAndVerifiedAtIsNullOrderByOtpIdDesc(User user, String type);
    Optional<OtpVerification> findTopByUser_EmailAndTypeAndVerifiedAtIsNullOrderByOtpIdDesc(String email, String type);
}