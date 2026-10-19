package com.example.cineone_admin.tool;

import com.example.cineone_admin.entity.MembershipTier;
import com.example.cineone_admin.entity.User;
import com.example.cineone_admin.enums.UserStatus;
import com.example.cineone_admin.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@SpringBootTest
public class AdminCreatorTool {

    private static final Logger log = LoggerFactory.getLogger(AdminCreatorTool.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    public void createAdminAccount() {

        String adminEmail = "admin@cineone.vn";
        String rawPassword = "1234567890"; 

        if (userRepository.findByEmail(adminEmail).isPresent()) {
            log.info(">> Tài khoản '{}' ĐÃ TỒN TẠI trong cơ sở dữ liệu. Bỏ qua tạo mới.", adminEmail);
            return;
        }

        List<MembershipTier> tiers = entityManager
                .createQuery("SELECT m FROM MembershipTier m", MembershipTier.class)
                .setMaxResults(1)
                .getResultList();

        MembershipTier tier = null;
        if (!tiers.isEmpty()) {
            tier = tiers.get(0);
        } else {
            try {
                entityManager.createNativeQuery(
                    "INSERT INTO membership_tiers (tier_id, name, min_spend) VALUES (1, 'STANDARD', 0)"
                ).executeUpdate();
                tier = entityManager.find(MembershipTier.class, 1L);
            } catch (Exception e) {
                log.warn("Bỏ qua tạo membership_tier: {}", e.getMessage());
            }
        }

        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setPhone("0901234567");
        admin.setFullName("Administrator");
        admin.setRole("ADMIN");
        admin.setStatus(UserStatus.ACTIVE.name());
        admin.setMembershipTier(tier);
        admin.setEmailVerified(true);
        admin.setPhoneVerified(true);
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));

        userRepository.save(admin);

        log.info("==================================================================");
        log.info(">> TẠO TÀI KHOẢN ADMIN THÀNH CÔNG <<");
        log.info(">> Email đăng nhập : {}", adminEmail);
        log.info(">> Mật khẩu gốc    : {}", rawPassword);
        log.info("==================================================================");
    }
}