package com.adb4.rmlmanager.repository;

import com.adb4.rmlmanager.entity.TextureSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for {@link TextureSet}.
 *
 * <p>Introduced by KAN-18 so LOD and mesh part deletion can reject requests
 * with dependent texture sets instead of failing on the foreign key.
 */
public interface TextureSetRepository extends JpaRepository<TextureSet, UUID> {

    boolean existsByLodId(UUID lodId);

    boolean existsByMeshPartId(UUID meshPartId);
}
