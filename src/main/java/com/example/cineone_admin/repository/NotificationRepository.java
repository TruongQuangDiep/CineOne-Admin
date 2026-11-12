package com.example.cineone_admin.repository;

import com.example.cineone_admin.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {
    long countByCampaign_CampaignId(Long campaignId);

    List<Notification> findByCampaign_CampaignIdOrderByNotificationIdDesc(Long campaignId);

    List<Notification> findByUser_UserIdOrderByCreatedAtDescNotificationIdDesc(Long userId);

    Optional<Notification> findByNotificationIdAndUser_UserId(Long notificationId, Long userId);

    long countByUser_UserIdAndReadFalse(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user.userId = :userId AND n.read = false")
    int markAllAsReadByUserId(@Param("userId") Long userId);
}