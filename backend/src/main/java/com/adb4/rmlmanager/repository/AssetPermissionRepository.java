package com.adb4.rmlmanager.repository;

import com.adb4.rmlmanager.entity.AssetPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetPermissionRepository extends JpaRepository<AssetPermission, UUID> {
    Optional<AssetPermission> findByAppUserIdAndAssetId(UUID userId, UUID assetId);
    List<AssetPermission> findByAssetId(UUID assetId);   // permission management UI
    void deleteByAppUserIdAndAssetId(UUID userId, UUID assetId);  // revocation
}
