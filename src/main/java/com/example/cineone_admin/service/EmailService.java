package com.example.cineone_admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async
    public void sendOtpEmail(String toEmail, String otpCode, String type) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);

            String subject = "EMAIL_VERIFY".equalsIgnoreCase(type)
                    ? "[CineOne] Xác thực tài khoản đăng ký"
                    : "[CineOne] Yêu cầu đặt lại mật khẩu";

            message.setSubject(subject);
            message.setText("Xin chào,\n\n"
                    + "Mã OTP CineOne của bạn là: " + otpCode + "\n"
                    + "Mã có hiệu lực trong vòng 5 phút. Vui lòng không chia sẻ mã này cho ai khác.\n\n"
                    + "Trân trọng,\n"
                    + "CineOne Team");

            mailSender.send(message);
            System.out.println(">>> Đã gửi email OTP (" + type + ") tới: " + toEmail);
        } catch (Exception e) {
            System.err.println(">>> Lỗi gửi mail SMTP tới " + toEmail + ": " + e.getMessage());
        }
    }
}