package com.example.cineone_admin.repository;

import com.example.cineone_admin.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {
    long countByCampaign_CampaignId(Long campaignId);

    List<Notification> findByCampaign_CampaignIdOrderByNotificationIdDesc(Long campaignId);

    List<Notification> findByUser_UserIdOrderByCreatedAtDescNotificationIdDesc(Long userId);
}
