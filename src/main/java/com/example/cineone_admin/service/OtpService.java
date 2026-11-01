package com.example.cineone_admin.service;

import com.example.cineone_admin.entity.OtpVerification;
import com.example.cineone_admin.entity.User;
import com.example.cineone_admin.repository.OtpVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpVerificationRepository otpRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Tạo OTP 6 chữ số, băm mật khẩu, lưu DB và gửi email SMTP
     */
    @Transactional
    public void generateAndSendOtp(User user, String type) {
        String rawOtp = String.format("%06d", new SecureRandom().nextInt(900000) + 100000);

        OtpVerification otp = new OtpVerification();
        otp.setUser(user);
        otp.setType(type);
        otp.setCodeHash(passwordEncoder.encode(rawOtp));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otp.setVerifiedAt(null);

        otpRepository.save(otp);

        emailService.sendOtpEmail(user.getEmail(), rawOtp, type);

        System.out.println(">>> [DEBUG OTP] Email: " + user.getEmail() + " | Type: " + type + " | Mã OTP: " + rawOtp);
    }

    /**
     * Kiểm tra mã OTP: đối chiếu hash, thời hạn và trạng thái verifiedAt
     */
    @Transactional
    public boolean verifyOtp(String email, String rawOtp, String type) {
        OtpVerification otp = otpRepository
                .findTopByUser_EmailAndTypeAndVerifiedAtIsNullOrderByOtpIdDesc(email, type)
                .orElse(null);

        if (otp == null) {
            return false;
        }

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }

        if (!passwordEncoder.matches(rawOtp, otp.getCodeHash())) {
            return false;
        }

        otp.setVerifiedAt(LocalDateTime.now());
        otpRepository.save(otp);
        return true;
    }
}