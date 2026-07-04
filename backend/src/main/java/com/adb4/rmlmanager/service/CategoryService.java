package com.adb4.rmlmanager.service;

import com.adb4.rmlmanager.dto.request.CategoryRequest;
import com.adb4.rmlmanager.dto.response.CategoryResponse;
import com.adb4.rmlmanager.entity.Category;
import com.adb4.rmlmanager.exception.DuplicateResourceException;
import com.adb4.rmlmanager.exception.ResourceInUseException;
import com.adb4.rmlmanager.exception.ResourceNotFoundException;
import com.adb4.rmlmanager.mapper.CategoryMapper;
import com.adb4.rmlmanager.repository.CategoryRepository;
import com.adb4.rmlmanager.repository.SubcategoryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryRepository categoryRepository,
                           SubcategoryRepository subcategoryRepository,
                           CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.categoryMapper = categoryMapper;
    }

    public List<CategoryResponse> findAll() {
        List<Category> categories = categoryRepository.findAll(Sort.by("name"));
        List<CategoryResponse> categoriesResponse = categories.stream()
                .map(categoryMapper::toResponse)
                .toList();
        return categoriesResponse;
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Category", "name", request.name());
        } else {
            Category category = categoryMapper.toEntity(request);
            return categoryMapper.toResponse(categoryRepository.save(category));
        }
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
            Category category = categoryRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
            if (!category.getName().equals(request.name())) {
                if (categoryRepository.existsByName(request.name())) {
                    throw new DuplicateResourceException("Category", "name", request.name());
                } else {
                    categoryMapper.updateEntity(request, category);
                }
            } else {
                categoryMapper.updateEntity(request, category);
            }
            return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(UUID id) {
        if (categoryRepository.existsById(id)) {
            if (subcategoryRepository.existsByCategoryId(id)) {
                throw new ResourceInUseException("category", "subcategories");
            } else {
                categoryRepository.deleteById(id);
            }
        } else {
            throw new ResourceNotFoundException("Category", "id", id);
        }
    }
}