package com.example.cineone_admin.repository;

import com.example.cineone_admin.entity.RoomTypeCatalog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomTypeCatalogRepository extends JpaRepository<RoomTypeCatalog, String> {
    List<RoomTypeCatalog> findAllByOrderByCodeAsc();
}
