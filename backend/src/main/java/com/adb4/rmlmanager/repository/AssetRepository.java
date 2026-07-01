package com.adb4.rmlmanager.repository;

import com.adb4.rmlmanager.entity.Asset;
import com.adb4.rmlmanager.enums.AssetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
    Optional<Asset> findByCode(String code);
    boolean existsByCode(String code);

    @Query(value = "SELECT a FROM Asset a JOIN FETCH a.subcategory s JOIN FETCH s.category",
            countQuery = "SELECT COUNT(a) FROM Asset a")
    Page<Asset> findAllWithCategory(Pageable pageable);

    Page<Asset> findBySubcategoryId(UUID subcategoryId, Pageable pageable);
    Page<Asset> findBySubcategoryCategoryId(UUID categoryId, Pageable pageable);
    Page<Asset> findByStatus(AssetStatus status, Pageable pageable);
    Page<Asset> findByTitleContainingIgnoreCase(String query, Pageable pageable);

    @Query("""
    SELECT a FROM Asset a JOIN FETCH a.subcategory s JOIN FETCH s.category
    WHERE a.status = 'PUBLISHED'
       OR a.createdBy = :userId
       OR EXISTS (SELECT 1 FROM AssetPermission p
                  WHERE p.assetId = a.id AND p.appUserId = :userId)
    """)
    Page<Asset> findAllVisibleTo(@Param("userId") UUID userId, Pageable pageable);

    // delete guards
    boolean existsBySubcategoryId(UUID subcategoryId);
}
