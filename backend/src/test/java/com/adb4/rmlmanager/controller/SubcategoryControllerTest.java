package com.adb4.rmlmanager.controller;

import com.adb4.rmlmanager.dto.response.SubcategoryResponse;
import com.adb4.rmlmanager.exception.DuplicateResourceException;
import com.adb4.rmlmanager.exception.ResourceInUseException;
import com.adb4.rmlmanager.exception.ResourceNotFoundException;
import com.adb4.rmlmanager.repository.AppUserRepository;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubcategoryController.class)
@Import(com.adb4.rmlmanager.security.SecurityConfig.class)
class SubcategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubcategoryService subcategoryService;

    @MockitoBean
    private AppUserRepository appUserRepository;

    private static final UUID CAT_ID = UUID.randomUUID();
    private static final UUID SUB_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.now();

    private SubcategoryResponse sampleResponse() {
        return new SubcategoryResponse(SUB_ID, "Chairs", CAT_ID, "Furniture", NOW, NOW);
    }

    // ---- GET /api/categories/{categoryId}/subcategories ----

    @Test
    @WithMockUser
    void findByCategoryId_returns200() throws Exception {
        when(subcategoryService.findByCategoryId(CAT_ID)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/categories/{categoryId}/subcategories", CAT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Chairs"))
                .andExpect(jsonPath("$[0].categoryName").value("Furniture"));
    }

    @Test
    @WithMockUser
    void findByCategoryId_whenCategoryNotFound_returns404() throws Exception {
        when(subcategoryService.findByCategoryId(CAT_ID))
                .thenThrow(new ResourceNotFoundException("Category", "id", CAT_ID));

        mockMvc.perform(get("/api/categories/{categoryId}/subcategories", CAT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    void findByCategoryId_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/categories/{categoryId}/subcategories", CAT_ID))
                .andExpect(status().isUnauthorized());
    }

    // ---- POST ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_asAdmin_returns201() throws Exception {
        when(subcategoryService.create(any(UUID.class), any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/categories/{categoryId}/subcategories", CAT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Chairs"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Chairs"));
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
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_withBlankName_returns400() throws Exception {
        mockMvc.perform(post("/api/categories/{categoryId}/subcategories", CAT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_whenCategoryNotFound_returns404() throws Exception {
        when(subcategoryService.create(any(UUID.class), any()))
                .thenThrow(new ResourceNotFoundException("Category", "id", CAT_ID));

        mockMvc.perform(post("/api/categories/{categoryId}/subcategories", CAT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Chairs"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_whenDuplicateName_returns409() throws Exception {
        when(subcategoryService.create(any(UUID.class), any()))
                .thenThrow(new DuplicateResourceException("Subcategory", "name", "Chairs"));

        mockMvc.perform(post("/api/categories/{categoryId}/subcategories", CAT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Chairs"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate Resource"));
    }

    // ---- PUT ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_asAdmin_returns200() throws Exception {
        when(subcategoryService.update(any(UUID.class), any(UUID.class), any()))
                .thenReturn(sampleResponse());

        mockMvc.perform(put("/api/categories/{categoryId}/subcategories/{id}", CAT_ID, SUB_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Chairs"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Chairs"));
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

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_whenSubcategoryNotFound_returns404() throws Exception {
        when(subcategoryService.update(any(UUID.class), any(UUID.class), any()))
                .thenThrow(new ResourceNotFoundException("Subcategory", "id", SUB_ID));

        mockMvc.perform(put("/api/categories/{categoryId}/subcategories/{id}", CAT_ID, SUB_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Chairs"}
                                """))
                .andExpect(status().isNotFound());
    }

    // ---- DELETE ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_asAdmin_returns204() throws Exception {
        doNothing().when(subcategoryService).delete(any(UUID.class), any(UUID.class));

        mockMvc.perform(delete("/api/categories/{categoryId}/subcategories/{id}", CAT_ID, SUB_ID)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void delete_asUser_returns403() throws Exception {
        mockMvc.perform(delete("/api/categories/{categoryId}/subcategories/{id}", CAT_ID, SUB_ID)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_whenNotFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Subcategory", "id", SUB_ID))
                .when(subcategoryService).delete(any(UUID.class), any(UUID.class));

        mockMvc.perform(delete("/api/categories/{categoryId}/subcategories/{id}", CAT_ID, SUB_ID)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_whenInUse_returns409() throws Exception {
        doThrow(new ResourceInUseException("Subcategory", "assets"))
                .when(subcategoryService).delete(any(UUID.class), any(UUID.class));

        mockMvc.perform(delete("/api/categories/{categoryId}/subcategories/{id}", CAT_ID, SUB_ID)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Resource In Use"));
    }
}