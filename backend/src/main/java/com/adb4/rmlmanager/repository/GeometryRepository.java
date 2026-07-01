package com.adb4.rmlmanager.repository;

import com.adb4.rmlmanager.entity.Geometry;
import com.adb4.rmlmanager.enums.GeometryFileType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GeometryRepository extends JpaRepository<Geometry, UUID> {
    Optional<Geometry> findByLodIdAndMeshPartIdAndFileTypeAndIsLatestTrue(UUID lodId, UUID meshPartId, GeometryFileType type);
    Optional<Geometry> findByLodIdAndMeshPartIsNullAndFileTypeAndIsLatestTrue(UUID lodId, GeometryFileType type);

    @Query("SELECT COALESCE(MAX(g.version), 0) FROM Geometry g WHERE g.lod.id = :lodId AND g.meshPart.id = :meshPartId AND g.fileType = :type")
    int findMaxVersion(@Param("lodId") UUID lodId, @Param("meshPartId") UUID meshPartId, @Param("type") GeometryFileType type);
    @Query("SELECT COALESCE(MAX(g.version), 0) FROM Geometry g WHERE g.lod.id = :lodId AND g.meshPart IS NULL AND g.fileType = :type")
    int findMaxVersionBaked(@Param("lodId") UUID lodId, @Param("type") GeometryFileType type);

    List<Geometry> findByLodIdOrderByVersionDesc(UUID lodId);  // version history view
}
