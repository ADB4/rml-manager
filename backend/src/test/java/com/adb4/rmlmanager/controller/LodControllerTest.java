package com.adb4.rmlmanager.controller;

import com.adb4.rmlmanager.dto.response.LodResponse;
import com.adb4.rmlmanager.exception.DuplicateResourceException;
import com.adb4.rmlmanager.exception.ResourceInUseException;
import com.adb4.rmlmanager.exception.ResourceNotFoundException;
import com.adb4.rmlmanager.repository.AppUserRepository;
import com.adb4.rmlmanager.service.LodService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LodController.class)
@AutoConfigureRestDocs
@Import(com.adb4.rmlmanager.security.SecurityConfig.class)  // real chain: CSRF disabled, HTTP Basic entry point
class LodControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LodService lodService;

    @MockitoBean
    private AppUserRepository appUserRepository;

    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID LOD_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.now();

    private LodResponse sampleLod(int level) {
        return new LodResponse(LOD_ID, ASSET_ID, level, NOW, NOW);
    }

    // ---- GET /api/assets/{assetId}/lods ----

    @Test
    @WithMockUser
    void findByAssetId_returns200WithList() throws Exception {
        when(lodService.findByAssetId(ASSET_ID)).thenReturn(List.of(
                sampleLod(0),
                new LodResponse(UUID.randomUUID(), ASSET_ID, 1, NOW, NOW)));

        mockMvc.perform(RestDocumentationRequestBuilders.get("/api/assets/{assetId}/lods", ASSET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].level").value(0))
                .andExpect(jsonPath("$[1].level").value(1))
                .andDo(document("lod-list",
                        pathParameters(
                                parameterWithName("assetId").description("Parent asset identifier (UUID)")
                        ),
                        responseFields(
                                fieldWithPath("[].id").description("LOD identifier"),
                                fieldWithPath("[].assetId").description("Parent asset identifier"),
                                fieldWithPath("[].level").description("Level of detail (0 is full detail)"),
                                fieldWithPath("[].createdAt").description("Creation timestamp"),
                                fieldWithPath("[].updatedAt").description("Last modification timestamp")
                        )));
    }

    @Test
    @WithMockUser
    void findByAssetId_whenAssetNotFound_returns404ProblemDetail() throws Exception {
        when(lodService.findByAssetId(ASSET_ID))
                .thenThrow(new ResourceNotFoundException("Asset", "id", ASSET_ID));

        mockMvc.perform(get("/api/assets/{assetId}/lods", ASSET_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andExpect(jsonPath("$.detail").value("Asset not found with id: " + ASSET_ID));
    }

    @Test
    void findByAssetId_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/assets/{assetId}/lods", ASSET_ID))
                .andExpect(status().isUnauthorized());
    }

    // ---- POST /api/assets/{assetId}/lods ----

    @Test
    @WithMockUser
    void create_returns201WithLod() throws Exception {
        when(lodService.create(eq(ASSET_ID), any())).thenReturn(sampleLod(0));

        mockMvc.perform(RestDocumentationRequestBuilders.post("/api/assets/{assetId}/lods", ASSET_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "level": 0
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(LOD_ID.toString()))
                .andExpect(jsonPath("$.assetId").value(ASSET_ID.toString()))
                .andExpect(jsonPath("$.level").value(0))
                .andDo(document("lod-create",
                        pathParameters(
                                parameterWithName("assetId").description("Parent asset identifier (UUID)")
                        ),
                        requestFields(
                                fieldWithPath("level").description("Level of detail (0 is full detail, unique per asset)")
                        ),
                        responseFields(
                                fieldWithPath("id").description("Generated LOD identifier"),
                                fieldWithPath("assetId").description("Parent asset identifier"),
                                fieldWithPath("level").description("Level of detail"),
                                fieldWithPath("createdAt").description("Creation timestamp"),
                                fieldWithPath("updatedAt").description("Last modification timestamp")
                        )));
    }

    @Test
    @WithMockUser
    void create_whenDuplicateLevel_returns409ProblemDetail() throws Exception {
        when(lodService.create(eq(ASSET_ID), any()))
                .thenThrow(new DuplicateResourceException("Lod", "level", 0));

        mockMvc.perform(post("/api/assets/{assetId}/lods", ASSET_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "level": 0
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Duplicate Resource"))
                .andExpect(jsonPath("$.detail").value("Lod already exists with level: 0"));
    }

    @Test
    @WithMockUser
    void create_whenLevelMissing_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/assets/{assetId}/lods", ASSET_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.level").exists());
    }

    @Test
    @WithMockUser
    void create_whenLevelNegative_returns400() throws Exception {
        mockMvc.perform(post("/api/assets/{assetId}/lods", ASSET_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "level": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.level").exists());
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/assets/{assetId}/lods", ASSET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "level": 0
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ---- DELETE /api/assets/{assetId}/lods/{lodId} ----

    @Test
    @WithMockUser
    void delete_returns204() throws Exception {
        mockMvc.perform(RestDocumentationRequestBuilders
                        .delete("/api/assets/{assetId}/lods/{lodId}", ASSET_ID, LOD_ID)
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andDo(document("lod-delete",
                        pathParameters(
                                parameterWithName("assetId").description("Parent asset identifier (UUID)"),
                                parameterWithName("lodId").description("LOD identifier (UUID)")
                        )));

        verify(lodService).delete(ASSET_ID, LOD_ID);
    }

    @Test
    @WithMockUser
    void delete_whenLodNotFound_returns404ProblemDetail() throws Exception {
        doThrow(new ResourceNotFoundException("Lod", "id", LOD_ID))
                .when(lodService).delete(ASSET_ID, LOD_ID);

        mockMvc.perform(delete("/api/assets/{assetId}/lods/{lodId}", ASSET_ID, LOD_ID)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    @WithMockUser
    void delete_whenLodInUse_returns409ProblemDetail() throws Exception {
        doThrow(new ResourceInUseException("Lod", "geometries"))
                .when(lodService).delete(ASSET_ID, LOD_ID);

        mockMvc.perform(delete("/api/assets/{assetId}/lods/{lodId}", ASSET_ID, LOD_ID)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Resource In Use"))
                .andExpect(jsonPath("$.detail").value("Cannot delete Lod because it has associated geometries"));
    }

    @Test
    void delete_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/assets/{assetId}/lods/{lodId}", ASSET_ID, LOD_ID))
                .andExpect(status().isUnauthorized());
    }
}
