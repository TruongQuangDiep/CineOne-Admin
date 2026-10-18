package com.example.cineone_admin.repository;

import com.example.cineone_admin.entity.DataProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataProviderRepository extends JpaRepository<DataProvider, Long> {
    Optional<DataProvider> findByCode(String code);
}
