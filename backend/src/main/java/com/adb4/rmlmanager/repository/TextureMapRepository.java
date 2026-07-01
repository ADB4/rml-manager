package com.adb4.rmlmanager.repository;

import com.adb4.rmlmanager.entity.TextureMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TextureMapRepository extends JpaRepository<TextureMap, UUID> {
    Optional<TextureMap> findByChecksum(String checksum);
}
