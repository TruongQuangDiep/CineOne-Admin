package com.example.cineone_admin.service;

import com.example.cineone_admin.entity.Ticket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

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

    @Async
    public void sendTicketEmail(String toEmail, String customerName, Ticket ticket) {
        if (toEmail == null || toEmail.isBlank() || !toEmail.contains("@")) {
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("[CineOne] Vé xem phim điện tử - Mã vé: " + ticket.getTicketCode());

            String timeStr = ticket.getShowtimeStart() != null
                    ? ticket.getShowtimeStart().format(DateTimeFormatter.ofPattern("HH:mm - dd/MM/yyyy"))
                    : "Hôm nay";

            String content = "Xin chào " + (customerName != null && !customerName.isBlank() ? customerName : "Quý khách") + ",\n\n"
                    + "Cảm ơn bạn đã lựa chọn CineOne! Giao dịch đặt vé của bạn đã hoàn tất thành công.\n"
                    + "Dưới đây là thông tin vé xem phim điện tử của bạn:\n\n"
                    + "==================================================\n"
                    + "🎬 TÊN PHIM: " + ticket.getMovieTitle() + "\n"
                    + "🏢 CỤM RẠP: " + ticket.getCinemaName() + " - " + ticket.getRoomName() + "\n"
                    + "⏰ SUẤT CHIẾU: " + timeStr + "\n"
                    + "💺 GHẾ ĐÃ ĐẶT: " + ticket.getSeatInfo() + "\n"
                    + "🎟️ MÃ VÉ CHECK-IN: " + ticket.getTicketCode() + "\n"
                    + "💵 TỔNG TIỀN: " + String.format("%,.0f đ", ticket.getTotalAmount()) + "\n"
                    + "==================================================\n\n"
                    + "📌 HƯỚNG DẪN CHECK-IN TẠI RẠP:\n"
                    + "- Khi đến rạp, bạn chỉ cần mở ứng dụng CineOne xuất trình mã QR Check-in hoặc cung cấp mã vé [" + ticket.getTicketCode() + "] cho nhân viên soát vé để vào phòng chiếu.\n\n"
                    + "Chúc bạn có những giây phút xem phim tuyệt vời tại CineOne!\n\n"
                    + "Trân trọng,\n"
                    + "Đội ngũ CineOne Cinema";

            message.setText(content);
            mailSender.send(message);
            System.out.println(">>> [CineOne] Đã gửi vé điện tử thành công tới Gmail: " + toEmail);
        } catch (Exception e) {
            System.err.println(">>> [CineOne] Lỗi khi gửi email vé điện tử tới " + toEmail + ": " + e.getMessage());
        }
    }
}