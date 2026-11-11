package com.example.cineone_admin.repository;

import com.example.cineone_admin.entity.NotificationCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationCampaignRepository extends JpaRepository<NotificationCampaign, Long>, JpaSpecificationExecutor<NotificationCampaign> {
    List<NotificationCampaign> findByStatusAndScheduledAtLessThanEqual(String status, LocalDateTime now);
}

