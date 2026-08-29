package com.adb4.rmlmanager.repository;

import com.adb4.rmlmanager.entity.MeshPart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link MeshPart}.
 *
 * <p>Introduced because geometry upload must resolve an optional
 * {@code meshPartId} to attach the row to a mesh part. The finders support
 * the mesh part management endpoints (KAN-18) nested under an asset.
 */
public interface MeshPartRepository extends JpaRepository<MeshPart, UUID> {

    List<MeshPart> findByAssetIdOrderByCodeAsc(UUID assetId);

    boolean existsByAssetIdAndCode(UUID assetId, String code);
}
