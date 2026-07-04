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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SubcategoryRepository subcategoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryService categoryService;

    private static final UUID CAT_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.now();

    private Category category(String name) {
        Category c = new Category();
        c.setId(CAT_ID);
        c.setName(name);
        return c;
    }

    private CategoryResponse response(String name) {
        return new CategoryResponse(CAT_ID, name, NOW, NOW);
    }

    // ---- findAll ----

    @Test
    void findAll_delegatesToRepositoryWithSortByName() {
        Category cat = category("Furniture");
        when(categoryRepository.findAll(Sort.by("name"))).thenReturn(List.of(cat));
        when(categoryMapper.toResponse(cat)).thenReturn(response("Furniture"));

        List<CategoryResponse> result = categoryService.findAll();

        assertEquals(1, result.size());
        assertEquals("Furniture", result.get(0).name());
        verify(categoryRepository).findAll(Sort.by("name"));
    }

    // ---- create ----

    @Test
    void create_savesAndReturnsResponse() {
        CategoryRequest request = new CategoryRequest("Lighting");
        Category entity = category("Lighting");

        when(categoryRepository.existsByName("Lighting")).thenReturn(false);
        when(categoryMapper.toEntity(request)).thenReturn(entity);
        when(categoryRepository.save(entity)).thenReturn(entity);
        when(categoryMapper.toResponse(entity)).thenReturn(response("Lighting"));

        CategoryResponse result = categoryService.create(request);

        assertEquals("Lighting", result.name());
        verify(categoryRepository).save(entity);
    }

    @Test
    void create_whenDuplicateName_throwsDuplicateResourceException() {
        when(categoryRepository.existsByName("Furniture")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> categoryService.create(new CategoryRequest("Furniture")));
        verify(categoryRepository, never()).save(any());
    }

    // ---- update ----

    @Test
    void update_whenNameChanged_updatesAndReturnsResponse() {
        Category existing = category("Old Name");
        CategoryRequest request = new CategoryRequest("New Name");

        when(categoryRepository.findById(CAT_ID)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByName("New Name")).thenReturn(false);
        when(categoryRepository.save(existing)).thenReturn(existing);
        when(categoryMapper.toResponse(existing)).thenReturn(response("New Name"));

        CategoryResponse result = categoryService.update(CAT_ID, request);

        assertEquals("New Name", result.name());
        verify(categoryMapper).updateEntity(request, existing);
        verify(categoryRepository).save(existing);
    }

    @Test
    void update_whenNameUnchanged_skipsDuplicateCheck() {
        Category existing = category("Same");
        CategoryRequest request = new CategoryRequest("Same");

        when(categoryRepository.findById(CAT_ID)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(existing)).thenReturn(existing);
        when(categoryMapper.toResponse(existing)).thenReturn(response("Same"));

        categoryService.update(CAT_ID, request);

        verify(categoryRepository, never()).existsByName(anyString());
    }

    @Test
    void update_whenNameChangedToDuplicate_throwsDuplicateResourceException() {
        Category existing = category("Original");

        when(categoryRepository.findById(CAT_ID)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByName("Taken")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> categoryService.update(CAT_ID, new CategoryRequest("Taken")));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void update_whenNotFound_throwsResourceNotFoundException() {
        when(categoryRepository.findById(CAT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> categoryService.update(CAT_ID, new CategoryRequest("Any")));
    }

    // ---- delete ----

    @Test
    void delete_removesCategory() {
        when(categoryRepository.existsById(CAT_ID)).thenReturn(true);
        when(subcategoryRepository.existsByCategoryId(CAT_ID)).thenReturn(false);

        categoryService.delete(CAT_ID);

        verify(categoryRepository).deleteById(CAT_ID);
    }

    @Test
    void delete_whenNotFound_throwsResourceNotFoundException() {
        when(categoryRepository.existsById(CAT_ID)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> categoryService.delete(CAT_ID));
        verify(categoryRepository, never()).deleteById(any());
    }

    @Test
    void delete_whenSubcategoriesExist_throwsResourceInUseException() {
        when(categoryRepository.existsById(CAT_ID)).thenReturn(true);
        when(subcategoryRepository.existsByCategoryId(CAT_ID)).thenReturn(true);

        assertThrows(ResourceInUseException.class,
                () -> categoryService.delete(CAT_ID));
        verify(categoryRepository, never()).deleteById(any());
    }
}