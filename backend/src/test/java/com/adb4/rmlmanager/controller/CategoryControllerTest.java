package com.adb4.rmlmanager.controller;

import com.adb4.rmlmanager.dto.response.CategoryResponse;
import com.adb4.rmlmanager.exception.DuplicateResourceException;
import com.adb4.rmlmanager.exception.ResourceInUseException;
import com.adb4.rmlmanager.exception.ResourceNotFoundException;
import com.adb4.rmlmanager.repository.AppUserRepository;
import com.adb4.rmlmanager.service.CategoryService;
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

@WebMvcTest(CategoryController.class)
@Import(com.adb4.rmlmanager.security.SecurityConfig.class)  // activates @EnableMethodSecurity
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private AppUserRepository appUserRepository;

    private static final UUID CAT_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.now();

    private CategoryResponse sampleResponse() {
        return new CategoryResponse(CAT_ID, "Furniture", NOW, NOW);
    }

    // ---- GET /api/categories ----

    @Test
    @WithMockUser
    void findAll_returns200WithCategories() throws Exception {
        when(categoryService.findAll()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Furniture"));
    }

    @Test
    void findAll_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized());
    }

    // ---- POST /api/categories ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_asAdmin_returns201() throws Exception {
        when(categoryService.create(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Furniture"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Furniture"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void create_asUser_returns403() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Furniture"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_withBlankName_returns400() throws Exception {
        mockMvc.perform(post("/api/categories")
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
    void create_withNameExceeding64Chars_returns400() throws Exception {
        String longName = "A".repeat(65);
        mockMvc.perform(post("/api/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s"}
                                """.formatted(longName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_whenDuplicateName_returns409() throws Exception {
        when(categoryService.create(any()))
                .thenThrow(new DuplicateResourceException("Category", "name", "Furniture"));

        mockMvc.perform(post("/api/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Furniture"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate Resource"));
    }

    // ---- PUT /api/categories/{id} ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_asAdmin_returns200() throws Exception {
        when(categoryService.update(any(UUID.class), any())).thenReturn(sampleResponse());

        mockMvc.perform(put("/api/categories/{id}", CAT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Furniture"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Furniture"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void update_asUser_returns403() throws Exception {
        mockMvc.perform(put("/api/categories/{id}", CAT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Furniture"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_whenNotFound_returns404() throws Exception {
        when(categoryService.update(any(UUID.class), any()))
                .thenThrow(new ResourceNotFoundException("Category", "id", CAT_ID));

        mockMvc.perform(put("/api/categories/{id}", CAT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Furniture"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_whenDuplicateName_returns409() throws Exception {
        when(categoryService.update(any(UUID.class), any()))
                .thenThrow(new DuplicateResourceException("Category", "name", "Furniture"));

        mockMvc.perform(put("/api/categories/{id}", CAT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Furniture"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate Resource"));
    }

    // ---- DELETE /api/categories/{id} ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_asAdmin_returns204() throws Exception {
        doNothing().when(categoryService).delete(any(UUID.class));

        mockMvc.perform(delete("/api/categories/{id}", CAT_ID)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void delete_asUser_returns403() throws Exception {
        mockMvc.perform(delete("/api/categories/{id}", CAT_ID)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_whenNotFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Category", "id", CAT_ID))
                .when(categoryService).delete(any(UUID.class));

        mockMvc.perform(delete("/api/categories/{id}", CAT_ID)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_whenInUse_returns409() throws Exception {
        doThrow(new ResourceInUseException("Category", "subcategories"))
                .when(categoryService).delete(any(UUID.class));

        mockMvc.perform(delete("/api/categories/{id}", CAT_ID)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Resource In Use"));
    }
}