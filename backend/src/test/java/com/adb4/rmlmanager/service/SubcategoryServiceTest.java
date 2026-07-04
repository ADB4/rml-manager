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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubcategoryServiceTest {

    @Mock
    private SubcategoryRepository subcategoryRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private SubcategoryMapper subcategoryMapper;

    @InjectMocks
    private SubcategoryService subcategoryService;

    private static final UUID CAT_ID = UUID.randomUUID();
    private static final UUID OTHER_CAT_ID = UUID.randomUUID();
    private static final UUID SUB_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.now();

    private Category category() {
        Category c = new Category();
        c.setId(CAT_ID);
        c.setName("Furniture");
        return c;
    }

    private Subcategory subcategory(String name) {
        Subcategory s = new Subcategory();
        s.setId(SUB_ID);
        s.setName(name);
        s.setCategory(category());
        return s;
    }

    private SubcategoryResponse response(String name) {
        return new SubcategoryResponse(SUB_ID, name, CAT_ID, "Furniture", NOW, NOW);
    }

    // ---- findByCategoryId ----

    @Test
    void findByCategoryId_returnsList() {
        Subcategory sub = subcategory("Chairs");
        when(categoryRepository.existsById(CAT_ID)).thenReturn(true);
        when(subcategoryRepository.findByCategoryIdOrderByName(CAT_ID)).thenReturn(List.of(sub));
        when(subcategoryMapper.toResponse(sub)).thenReturn(response("Chairs"));

        List<SubcategoryResponse> result = subcategoryService.findByCategoryId(CAT_ID);

        assertEquals(1, result.size());
        assertEquals("Chairs", result.get(0).name());
    }

    @Test
    void findByCategoryId_whenCategoryNotFound_throwsResourceNotFoundException() {
        when(categoryRepository.existsById(CAT_ID)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> subcategoryService.findByCategoryId(CAT_ID));
    }

    // ---- create ----

    @Test
    void create_savesAndReturnsResponse() {
        Category cat = category();
        SubcategoryRequest request = new SubcategoryRequest("Tables");
        Subcategory entity = subcategory("Tables");

        when(categoryRepository.findById(CAT_ID)).thenReturn(Optional.of(cat));
        when(subcategoryRepository.existsByCategoryIdAndName(CAT_ID, "Tables")).thenReturn(false);
        when(subcategoryMapper.toEntity(request, cat)).thenReturn(entity);
        when(subcategoryRepository.save(entity)).thenReturn(entity);
        when(subcategoryMapper.toResponse(entity)).thenReturn(response("Tables"));

        SubcategoryResponse result = subcategoryService.create(CAT_ID, request);

        assertEquals("Tables", result.name());
        verify(subcategoryRepository).save(entity);
    }

    @Test
    void create_whenCategoryNotFound_throwsResourceNotFoundException() {
        when(categoryRepository.findById(CAT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> subcategoryService.create(CAT_ID, new SubcategoryRequest("Chairs")));
        verify(subcategoryRepository, never()).save(any());
    }

    @Test
    void create_whenDuplicateNameInCategory_throwsDuplicateResourceException() {
        when(categoryRepository.findById(CAT_ID)).thenReturn(Optional.of(category()));
        when(subcategoryRepository.existsByCategoryIdAndName(CAT_ID, "Chairs")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> subcategoryService.create(CAT_ID, new SubcategoryRequest("Chairs")));
        verify(subcategoryRepository, never()).save(any());
    }

    // ---- update ----

    @Test
    void update_whenNameChanged_updatesAndReturnsResponse() {
        Subcategory existing = subcategory("Old");
        SubcategoryRequest request = new SubcategoryRequest("New");

        when(subcategoryRepository.findById(SUB_ID)).thenReturn(Optional.of(existing));
        when(subcategoryRepository.existsByCategoryIdAndName(CAT_ID, "New")).thenReturn(false);
        when(subcategoryRepository.save(existing)).thenReturn(existing);
        when(subcategoryMapper.toResponse(existing)).thenReturn(response("New"));

        SubcategoryResponse result = subcategoryService.update(CAT_ID, SUB_ID, request);

        assertEquals("New", result.name());
        verify(subcategoryMapper).updateEntity(request, existing);
    }

    @Test
    void update_whenNameUnchanged_skipsDuplicateCheck() {
        Subcategory existing = subcategory("Same");
        SubcategoryRequest request = new SubcategoryRequest("Same");

        when(subcategoryRepository.findById(SUB_ID)).thenReturn(Optional.of(existing));
        when(subcategoryRepository.save(existing)).thenReturn(existing);
        when(subcategoryMapper.toResponse(existing)).thenReturn(response("Same"));

        subcategoryService.update(CAT_ID, SUB_ID, request);

        verify(subcategoryRepository, never()).existsByCategoryIdAndName(any(), anyString());
    }

    @Test
    void update_whenNameChangedToDuplicate_throwsDuplicateResourceException() {
        Subcategory existing = subcategory("Original");

        when(subcategoryRepository.findById(SUB_ID)).thenReturn(Optional.of(existing));
        when(subcategoryRepository.existsByCategoryIdAndName(CAT_ID, "Taken")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> subcategoryService.update(CAT_ID, SUB_ID, new SubcategoryRequest("Taken")));
        verify(subcategoryRepository, never()).save(any());
    }

    @Test
    void update_whenSubcategoryNotFound_throwsResourceNotFoundException() {
        when(subcategoryRepository.findById(SUB_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> subcategoryService.update(CAT_ID, SUB_ID, new SubcategoryRequest("Any")));
    }

    @Test
    void update_whenSubcategoryBelongsToDifferentCategory_throwsResourceNotFoundException() {
        Subcategory wrongParent = subcategory("Chairs");
        Category other = new Category();
        other.setId(OTHER_CAT_ID);
        wrongParent.setCategory(other);

        when(subcategoryRepository.findById(SUB_ID)).thenReturn(Optional.of(wrongParent));

        assertThrows(ResourceNotFoundException.class,
                () -> subcategoryService.update(CAT_ID, SUB_ID, new SubcategoryRequest("Chairs")));
    }

    // ---- delete ----

    @Test
    void delete_removesSubcategory() {
        Subcategory existing = subcategory("Chairs");

        when(subcategoryRepository.findById(SUB_ID)).thenReturn(Optional.of(existing));
        when(assetRepository.existsBySubcategoryId(SUB_ID)).thenReturn(false);

        subcategoryService.delete(CAT_ID, SUB_ID);

        verify(subcategoryRepository).delete(existing);
    }

    @Test
    void delete_whenSubcategoryNotFound_throwsResourceNotFoundException() {
        when(subcategoryRepository.findById(SUB_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> subcategoryService.delete(CAT_ID, SUB_ID));
        verify(subcategoryRepository, never()).delete(any());
    }

    @Test
    void delete_whenSubcategoryBelongsToDifferentCategory_throwsResourceNotFoundException() {
        Subcategory wrongParent = subcategory("Chairs");
        Category other = new Category();
        other.setId(OTHER_CAT_ID);
        wrongParent.setCategory(other);

        when(subcategoryRepository.findById(SUB_ID)).thenReturn(Optional.of(wrongParent));

        assertThrows(ResourceNotFoundException.class,
                () -> subcategoryService.delete(CAT_ID, SUB_ID));
        verify(subcategoryRepository, never()).delete(any());
    }

    @Test
    void delete_whenAssetsExist_throwsResourceInUseException() {
        Subcategory existing = subcategory("Chairs");

        when(subcategoryRepository.findById(SUB_ID)).thenReturn(Optional.of(existing));
        when(assetRepository.existsBySubcategoryId(SUB_ID)).thenReturn(true);

        assertThrows(ResourceInUseException.class,
                () -> subcategoryService.delete(CAT_ID, SUB_ID));
        verify(subcategoryRepository, never()).delete(any());
    }
}