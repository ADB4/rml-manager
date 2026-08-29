package com.adb4.rmlmanager.repository;

import com.adb4.rmlmanager.entity.MeshPart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for {@link MeshPart}.
 *
 * <p>Introduced here because geometry upload must resolve an optional
 * {@code meshPartId} to attach the row to a mesh part. KAN-18 (LOD and mesh
 * part management endpoints) also calls for this repository; this is the
 * minimal declaration both stories share. When KAN-18 lands it can add its
 * own finders (for example {@code existsByAssetIdAndCode}) on top of this.
 */
public interface MeshPartRepository extends JpaRepository<MeshPart, UUID> {
}
