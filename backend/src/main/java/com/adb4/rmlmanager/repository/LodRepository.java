package com.adb4.rmlmanager.repository;

import com.adb4.rmlmanager.entity.Lod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LodRepository extends JpaRepository<Lod, UUID> {
}
