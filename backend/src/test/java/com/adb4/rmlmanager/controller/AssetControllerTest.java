package com.adb4.rmlmanager.controller;

import com.adb4.rmlmanager.exception.DuplicateResourceException;
import com.adb4.rmlmanager.exception.ResourceNotFoundException;
import com.adb4.rmlmanager.repository.AppUserRepository;
import com.adb4.rmlmanager.service.AssetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AssetController.class)
class AssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssetService assetService;

    @MockitoBean
    private AppUserRepository appUserRepository;

    @Test
    @WithMockUser
    void getByCode_whenNotFound_returns404ProblemDetail() throws Exception {
        when(assetService.findByCode("MISSING"))
                .thenThrow(new ResourceNotFoundException("Asset", "code", "MISSING"));

        mockMvc.perform(get("/api/assets/MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Asset not found with code: MISSING"));
    }

    @Test
    @WithMockUser
    void create_whenDuplicateCode_returns409ProblemDetail() throws Exception {
        when(assetService.create(any()))
                .thenThrow(new DuplicateResourceException("Asset", "code", "DUP001"));

        mockMvc.perform(post("/api/assets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "code": "DUP001",
                                    "title": "Duplicate Asset",
                                    "subcategoryId": "00000000-0000-0000-0000-000000000001",
                                    "hasAnimation": false
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Duplicate Resource"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Asset already exists with code: DUP001"));
    }

    @Test
    @WithMockUser
    void create_whenValidationFails_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/assets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "code": "",
                                    "title": "",
                                    "subcategoryId": null,
                                    "hasAnimation": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors").isMap());
    }

    @Test
    @WithMockUser
    void update_whenSubcategoryNotFound_returns404ProblemDetail() throws Exception {
        UUID assetId = UUID.randomUUID();
        UUID missingSubcategoryId = UUID.randomUUID();

        when(assetService.update(any(UUID.class), any()))
                .thenThrow(new ResourceNotFoundException("Subcategory", "id", missingSubcategoryId));

        mockMvc.perform(put("/api/assets/{id}", assetId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Updated",
                                    "subcategoryId": "%s",
                                    "hasAnimation": false,
                                    "status": "DRAFT"
                                }
                                """.formatted(missingSubcategoryId)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    @WithMockUser
    void getByCode_whenUnhandledException_returns500WithoutStackTrace() throws Exception {
        when(assetService.findByCode("BROKEN"))
                .thenThrow(new RuntimeException("sensitive internal detail"));

        mockMvc.perform(get("/api/assets/BROKEN"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }
}