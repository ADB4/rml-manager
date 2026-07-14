package com.adb4.rmlmanager.controller;

import com.adb4.rmlmanager.dto.response.SubcategoryResponse;
import com.adb4.rmlmanager.entity.Category;
import com.adb4.rmlmanager.entity.Subcategory;
import com.adb4.rmlmanager.mapper.SubcategoryMapper;
import com.adb4.rmlmanager.repository.AppUserRepository;
import com.adb4.rmlmanager.repository.AssetRepository;
import com.adb4.rmlmanager.repository.CategoryRepository;
import com.adb4.rmlmanager.repository.SubcategoryRepository;
import com.adb4.rmlmanager.security.SecurityConfig;
import com.adb4.rmlmanager.service.SubcategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubcategoryController.class)
@Import({SubcategoryService.class, SecurityConfig.class})
class SubcategoryControllerServiceIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubcategoryRepository subcategoryRepository;

    @MockitoBean
    private CategoryRepository categoryRepository;

    @MockitoBean
    private AssetRepository assetRepository;

    @MockitoBean
    private SubcategoryMapper subcategoryMapper;

    @MockitoBean
    private AppUserRepository appUserRepository;

    private static final UUID CAT_ID = UUID.randomUUID();
    private static final UUID SUB_ID = UUID.randomUUID();
    private static final UUID OTHER_CAT_ID = UUID.randomUUID();
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

    // ---- GET /api/categories/{categoryId}/subcategories ----

    @Test
    @WithMockUser
    void findByCategoryId_existingCategory_returnsSubcategories() throws Exception {
        Subcategory entity = subcategory("Chairs");

        when(categoryRepository.existsById(CAT_ID)).thenReturn(true);
        when(subcategoryRepository.findByCategoryIdOrderByName(CAT_ID)).thenReturn(List.of(entity));
        when(subcategoryMapper.toResponse(entity)).thenReturn(response("Chairs"));

        mockMvc.perform(get("/api/categories/{categoryId}/subcategories", CAT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Chairs"))
                .andExpect(jsonPath("$[0].categoryId").value(CAT_ID.toString()))
                .andExpect(jsonPath("$[0].categoryName").value("Furniture"));
    }

    @Test
    @WithMockUser
    void findByCategoryId_missingCategory_returns404() throws Exception {
        when(categoryRepository.existsById(CAT_ID)).thenReturn(false);

        mockMvc.perform(get("/api/categories/{categoryId}/subcategories", CAT_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void findByCategoryId_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/categories/{categoryId}/subcategories", CAT_ID))
                .andExpect(status().isUnauthorized());
    }

    // ---- POST ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_asAdmin_validRequest_returns201() throws Exception {
        Category cat = category();
        Subcategory entity = subcategory("Chairs");

        when(categoryRepository.findById(CAT_ID)).thenReturn(Optional.of(cat));
        when(subcategoryRepository.existsByCategoryIdAndName(CAT_ID, "Chairs")).thenReturn(false);
        when(subcategoryMapper.toEntity(any(), eq(cat))).thenReturn(entity);
        when(subcategoryRepository.save(entity)).thenReturn(entity);
        when(subcategoryMapper.toResponse(entity)).thenReturn(response("Chairs"));

        mockMvc.perform(post("/api/categories/{categoryId}/subcategories", CAT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Chairs"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Chairs"))
                .andExpect(jsonPath("$.categoryName").value("Furniture"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_asAdmin_categoryNotFound_returns404() throws Exception {
        when(categoryRepository.findById(CAT_ID)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/categories/{categoryId}/subcategories", CAT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Chairs"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_asAdmin_duplicateNameInCategory_returns409() throws Exception {
        when(categoryRepository.findById(CAT_ID)).thenReturn(Optional.of(category()));
        when(subcategoryRepository.existsByCategoryIdAndName(CAT_ID, "Chairs")).thenReturn(true);

        mockMvc.perform(post("/api/categories/{categoryId}/subcategories", CAT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Chairs"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate Resource"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void create_asUser_returns403() throws Exception {
        mockMvc.perform(post("/api/categories/{categoryId}/subcategories", CAT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Chairs"}
                                """))
                .andExpect(status().isForbidden());

        verify(subcategoryRepository, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_asAdmin_blankName_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/categories/{categoryId}/subcategories", CAT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());

        verify(categoryRepository, never()).findById(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_asAdmin_nameExceeds64Chars_returns400WithFieldErrors() throws Exception {
        String longName = "A".repeat(65);

        mockMvc.perform(post("/api/categories/{categoryId}/subcategories", CAT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s"}
                                """.formatted(longName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());

        verify(categoryRepository, never()).findById(any());
    }

    // ---- PUT ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_asAdmin_validRequest_returns200() throws Exception {
        Subcategory existing = subcategory("Old");

        when(subcategoryRepository.findById(SUB_ID)).thenReturn(Optional.of(existing));
        when(subcategoryRepository.existsByCategoryIdAndName(CAT_ID, "New")).thenReturn(false);
        when(subcategoryRepository.save(existing)).thenReturn(existing);
        when(subcategoryMapper.toResponse(existing)).thenReturn(response("New"));

        mockMvc.perform(put("/api/categories/{categoryId}/subcategories/{id}", CAT_ID, SUB_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "New"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"));

        verify(subcategoryMapper).updateEntity(any(), eq(existing));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_asAdmin_unchangedName_skipsDuplicateCheck() throws Exception {
        Subcategory existing = subcategory("Same");

        when(subcategoryRepository.findById(SUB_ID)).thenReturn(Optional.of(existing));
        when(subcategoryRepository.save(existing)).thenReturn(existing);
        when(subcategoryMapper.toResponse(existing)).thenReturn(response("Same"));

        mockMvc.perform(put("/api/categories/{categoryId}/subcategories/{id}", CAT_ID, SUB_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Same"}
                                """))
                .andExpect(status().isOk());

        verify(subcategoryRepository, never()).existsByCategoryIdAndName(any(), anyString());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_asAdmin_duplicateName_returns409() throws Exception {
        Subcategory existing = subcategory("Original");

        when(subcategoryRepository.findById(SUB_ID)).thenReturn(Optional.of(existing));
        when(subcategoryRepository.existsByCategoryIdAndName(CAT_ID, "Taken")).thenReturn(true);

        mockMvc.perform(put("/api/categories/{categoryId}/subcategories/{id}", CAT_ID, SUB_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Taken"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate Resource"));

        verify(subcategoryRepository, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_asAdmin_subcategoryNotFound_returns404() throws Exception {
        when(subcategoryRepository.findById(SUB_ID)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/categories/{categoryId}/subcategories/{id}", CAT_ID, SUB_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Chairs"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_asAdmin_subcategoryBelongsToDifferentCategory_returns404() throws Exception {
        Subcategory wrongParent = subcategory("Chairs");
        Category other = new Category();
        other.setId(OTHER_CAT_ID);
        wrongParent.setCategory(other);

        when(subcategoryRepository.findById(SUB_ID)).thenReturn(Optional.of(wrongParent));

        mockMvc.perform(put("/api/categories/{categoryId}/subcategories/{id}", CAT_ID, SUB_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Chairs"}
                                """))
                .andExpect(status().isNotFound());

        verify(subcategoryRepository, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "USER")
    void update_asUser_returns403() throws Exception {
        mockMvc.perform(put("/api/categories/{categoryId}/subcategories/{id}", CAT_ID, SUB_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Chairs"}
                                """))
                .andExpect(status().isForbidden());
    }

    // ---- DELETE ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_asAdmin_returns204() throws Exception {
        Subcategory existing = subcategory("Chairs");

        when(subcategoryRepository.findById(SUB_ID)).thenReturn(Optional.of(existing));
        when(assetRepository.existsBySubcategoryId(SUB_ID)).thenReturn(false);

        mockMvc.perform(delete("/api/categories/{categoryId}/subcategories/{id}", CAT_ID, SUB_ID)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(subcategoryRepository).delete(existing);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_asAdmin_whenInUse_returns409() throws Exception {
        Subcategory existing = subcategory("Chairs");

        when(subcategoryRepository.findById(SUB_ID)).thenReturn(Optional.of(existing));
        when(assetRepository.existsBySubcategoryId(SUB_ID)).thenReturn(true);

        mockMvc.perform(delete("/api/categories/{categoryId}/subcategories/{id}", CAT_ID, SUB_ID)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Resource In Use"));

        verify(subcategoryRepository, never()).delete(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_asAdmin_subcategoryNotFound_returns404() throws Exception {
        when(subcategoryRepository.findById(SUB_ID)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/categories/{categoryId}/subcategories/{id}", CAT_ID, SUB_ID)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void delete_asUser_returns403() throws Exception {
        mockMvc.perform(delete("/api/categories/{categoryId}/subcategories/{id}", CAT_ID, SUB_ID)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}