package com.example.cineone_admin.repository;

import com.example.cineone_admin.entity.SupportRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long>,
        JpaSpecificationExecutor<SupportRequest> {

    long countByStatus(String status);

    List<SupportRequest> findByStatusOrderByCreatedAtDesc(String status);
}