package com.example.cineone_admin.repository;

import com.example.cineone_admin.entity.KnowledgeBaseEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeBaseEntryRepository extends JpaRepository<KnowledgeBaseEntry, Long>,
        JpaSpecificationExecutor<KnowledgeBaseEntry> {

    List<KnowledgeBaseEntry> findByCategoryAndStatus(String category, String status);

    boolean existsByTitleIgnoreCaseAndKnowledgeIdNot(String title, Long knowledgeId);

    boolean existsByTitleIgnoreCase(String title);
}