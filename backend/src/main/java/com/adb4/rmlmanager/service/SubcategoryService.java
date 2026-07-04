package com.adb4.rmlmanager.service;

import com.adb4.rmlmanager.dto.request.SubcategoryRequest;
import com.adb4.rmlmanager.dto.response.SubcategoryResponse;
import com.adb4.rmlmanager.entity.Category;
import com.adb4.rmlmanager.entity.Subcategory;
import com.adb4.rmlmanager.exception.DuplicateResourceException;
import com.adb4.rmlmanager.exception.ResourceInUseException;
import com.adb4.rmlmanager.exception.ResourceNotFoundException;
import com.adb4.rmlmanager.mapper.SubcategoryMapper;
import com.adb4.rmlmanager.repository.AssetRepository;
import com.adb4.rmlmanager.repository.CategoryRepository;
import com.adb4.rmlmanager.repository.SubcategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SubcategoryService {

    private final SubcategoryRepository subcategoryRepository;
    private final CategoryRepository categoryRepository;
    private final AssetRepository assetRepository;
    private final SubcategoryMapper subcategoryMapper;

    public SubcategoryService(SubcategoryRepository subcategoryRepository,
                              CategoryRepository categoryRepository,
                              AssetRepository assetRepository,
                              SubcategoryMapper subcategoryMapper) {
        this.subcategoryRepository = subcategoryRepository;
        this.categoryRepository = categoryRepository;
        this.assetRepository = assetRepository;
        this.subcategoryMapper = subcategoryMapper;
    }

    public List<SubcategoryResponse> findByCategoryId(UUID categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category", "id", categoryId);
        }
        else {
            List<Subcategory> subcategories = subcategoryRepository.findByCategoryIdOrderByName(categoryId);
            List<SubcategoryResponse> subcategoriesResponse = subcategories.stream()
                    .map(subcategoryMapper::toResponse)
                    .toList();
            return subcategoriesResponse;
        }
    }

    @Transactional
    public SubcategoryResponse create(UUID categoryId, SubcategoryRequest request) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId));
        if (subcategoryRepository.existsByCategoryIdAndName(categoryId, request.name())) {
            throw new  DuplicateResourceException("Subcategory", "name", request.name());
        } else {
            Subcategory subcategory = subcategoryMapper.toEntity(request, category);
            return subcategoryMapper.toResponse(subcategoryRepository.save(subcategory));
        }
    }

    @Transactional
    public SubcategoryResponse update(UUID categoryId, UUID id, SubcategoryRequest request) {
        Subcategory subcategory = subcategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subcategory", "id", id));
        if (subcategory.getCategory().getId().equals(categoryId)) {
            if (!request.name().equals(subcategory.getName())) {
                if (subcategoryRepository.existsByCategoryIdAndName(categoryId, request.name())) {
                    throw new DuplicateResourceException("Subcategory", "name", request.name());
                }
            }
            subcategoryMapper.updateEntity(request, subcategory);
            subcategory = subcategoryRepository.save(subcategory);
            return subcategoryMapper.toResponse(subcategory);
        } else {
            throw new ResourceNotFoundException("Subcategory", "id", id);
        }
    }

    @Transactional
    public void delete(UUID categoryId, UUID id) {
        Subcategory subcategory = subcategoryRepository.findById(id)
                .orElseThrow(()  -> new ResourceNotFoundException("Subcategory", "id", id));
        if (subcategory.getCategory().getId() == categoryId) {
            if (assetRepository.existsBySubcategoryId(id)) {
                throw new ResourceInUseException("Subcategory", "assets");
            } else {
                subcategoryRepository.delete(subcategory);
            }
        } else {
            throw new ResourceNotFoundException("Subcategory", "id", id);
        }
    }
}