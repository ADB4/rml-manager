package com.adb4.rmlmanager.repository;

import com.adb4.rmlmanager.entity.Subcategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubcategoryRepository extends JpaRepository<Subcategory, UUID> {
    List<Subcategory> findByCategoryIdOrderByName(UUID categoryId);
    boolean existsByCategoryId(UUID categoryId);
}
