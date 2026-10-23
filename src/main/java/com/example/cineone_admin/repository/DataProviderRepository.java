package com.example.cineone_admin.repository;

import com.example.cineone_admin.entity.DataProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DataProviderRepository extends JpaRepository<DataProvider, Long>, JpaSpecificationExecutor<DataProvider> {
    Optional<DataProvider> findByCode(String code);
}
