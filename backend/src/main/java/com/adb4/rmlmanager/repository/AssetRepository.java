package com.adb4.rmlmanager.repository;

import com.adb4.rmlmanager.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
    Optional<Asset> findByCode(String code);
    boolean existsByCode(String code);
}
