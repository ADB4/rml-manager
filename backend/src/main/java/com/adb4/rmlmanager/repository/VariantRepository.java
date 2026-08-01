package com.adb4.rmlmanager.repository;

import com.adb4.rmlmanager.entity.Variant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VariantRepository extends JpaRepository<Variant, UUID> {

    /**
     * Nulls sort last under Postgres' default ASC ordering, so variants without
     * an explicit sortOrder fall to the bottom and tie-break on code.
     */
    List<Variant> findByAssetIdOrderBySortOrderAscCodeAsc(UUID assetId);

    List<Variant> findByAssetIdAndIsDefaultTrue(UUID assetId);

    boolean existsByAssetIdAndCode(UUID assetId, String code);

    boolean existsByAssetId(UUID assetId);
}