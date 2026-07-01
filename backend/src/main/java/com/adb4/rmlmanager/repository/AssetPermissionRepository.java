package com.adb4.rmlmanager.repository;

import com.adb4.rmlmanager.entity.AssetPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssetPermissionRepository extends JpaRepository<AssetPermission, UUID> {
    Optional<AssetPermission> findByPermissionId(UUID permissionId);
}
