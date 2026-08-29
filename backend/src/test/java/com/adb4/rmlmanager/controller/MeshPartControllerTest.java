package com.adb4.rmlmanager.controller;

import com.adb4.rmlmanager.dto.response.MeshPartResponse;
import com.adb4.rmlmanager.exception.DuplicateResourceException;
import com.adb4.rmlmanager.exception.ResourceInUseException;
import com.adb4.rmlmanager.exception.ResourceNotFoundException;
import com.adb4.rmlmanager.repository.AppUserRepository;
import com.adb4.rmlmanager.service.MeshPartService;
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

@WebMvcTest(MeshPartController.class)
@AutoConfigureRestDocs
@Import(com.adb4.rmlmanager.security.SecurityConfig.class)  // real chain: CSRF disabled, HTTP Basic entry point
class MeshPartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MeshPartService meshPartService;

    @MockitoBean
    private AppUserRepository appUserRepository;

    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID MESH_PART_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.now();

    private MeshPartResponse samplePart(String code) {
        return new MeshPartResponse(MESH_PART_ID, ASSET_ID, code, "standard", "oak", NOW, NOW);
    }

    // ---- GET /api/assets/{assetId}/mesh-parts ----

    @Test
    @WithMockUser
    void findByAssetId_returns200WithList() throws Exception {
        when(meshPartService.findByAssetId(ASSET_ID)).thenReturn(List.of(samplePart("seat")));

        mockMvc.perform(RestDocumentationRequestBuilders.get("/api/assets/{assetId}/mesh-parts", ASSET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("seat"))
                .andExpect(jsonPath("$[0].shader").value("standard"))
                .andDo(document("mesh-part-list",
                        pathParameters(
                                parameterWithName("assetId").description("Parent asset identifier (UUID)")
                        ),
                        responseFields(
                                fieldWithPath("[].id").description("Mesh part identifier"),
                                fieldWithPath("[].assetId").description("Parent asset identifier"),
                                fieldWithPath("[].code").description("Mesh part code (unique per asset)"),
                                fieldWithPath("[].shader").description("Shader name"),
                                fieldWithPath("[].material").description("Material name"),
                                fieldWithPath("[].createdAt").description("Creation timestamp"),
                                fieldWithPath("[].updatedAt").description("Last modification timestamp")
                        )));
    }

    @Test
    @WithMockUser
    void findByAssetId_whenAssetNotFound_returns404ProblemDetail() throws Exception {
        when(meshPartService.findByAssetId(ASSET_ID))
                .thenThrow(new ResourceNotFoundException("Asset", "id", ASSET_ID));

        mockMvc.perform(get("/api/assets/{assetId}/mesh-parts", ASSET_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andExpect(jsonPath("$.detail").value("Asset not found with id: " + ASSET_ID));
    }

    @Test
    void findByAssetId_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/assets/{assetId}/mesh-parts", ASSET_ID))
                .andExpect(status().isUnauthorized());
    }

    // ---- POST /api/assets/{assetId}/mesh-parts ----

    @Test
    @WithMockUser
    void create_returns201WithMeshPart() throws Exception {
        when(meshPartService.create(eq(ASSET_ID), any())).thenReturn(samplePart("seat"));

        mockMvc.perform(RestDocumentationRequestBuilders.post("/api/assets/{assetId}/mesh-parts", ASSET_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "code": "seat",
                                    "shader": "standard",
                                    "material": "oak"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(MESH_PART_ID.toString()))
                .andExpect(jsonPath("$.assetId").value(ASSET_ID.toString()))
                .andExpect(jsonPath("$.code").value("seat"))
                .andDo(document("mesh-part-create",
                        pathParameters(
                                parameterWithName("assetId").description("Parent asset identifier (UUID)")
                        ),
                        requestFields(
                                fieldWithPath("code").description("Mesh part code (unique per asset, max 64 characters)"),
                                fieldWithPath("shader").optional()
                                        .description("Optional shader name (max 255 characters)"),
                                fieldWithPath("material").optional()
                                        .description("Optional material name (max 255 characters)")
                        ),
                        responseFields(
                                fieldWithPath("id").description("Generated mesh part identifier"),
                                fieldWithPath("assetId").description("Parent asset identifier"),
                                fieldWithPath("code").description("Mesh part code"),
                                fieldWithPath("shader").description("Shader name"),
                                fieldWithPath("material").description("Material name"),
                                fieldWithPath("createdAt").description("Creation timestamp"),
                                fieldWithPath("updatedAt").description("Last modification timestamp")
                        )));
    }

    @Test
    @WithMockUser
    void create_whenDuplicateCode_returns409ProblemDetail() throws Exception {
        when(meshPartService.create(eq(ASSET_ID), any()))
                .thenThrow(new DuplicateResourceException("MeshPart", "code", "seat"));

        mockMvc.perform(post("/api/assets/{assetId}/mesh-parts", ASSET_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "code": "seat"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Duplicate Resource"))
                .andExpect(jsonPath("$.detail").value("MeshPart already exists with code: seat"));
    }

    @Test
    @WithMockUser
    void create_whenCodeBlank_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/assets/{assetId}/mesh-parts", ASSET_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "code": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.code").exists());
    }

    @Test
    @WithMockUser
    void create_whenCodeExceeds64Chars_returns400() throws Exception {
        mockMvc.perform(post("/api/assets/{assetId}/mesh-parts", ASSET_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "code": "%s"
                                }
                                """.formatted("A".repeat(65))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.code").exists());
    }

    @Test
    @WithMockUser
    void create_whenShaderExceeds255Chars_returns400() throws Exception {
        mockMvc.perform(post("/api/assets/{assetId}/mesh-parts", ASSET_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "code": "seat",
                                    "shader": "%s"
                                }
                                """.formatted("A".repeat(256))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.shader").exists());
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/assets/{assetId}/mesh-parts", ASSET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "code": "seat"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ---- PUT /api/assets/{assetId}/mesh-parts/{meshPartId} ----

    @Test
    @WithMockUser
    void update_returns200WithUpdatedMeshPart() throws Exception {
        when(meshPartService.update(eq(ASSET_ID), eq(MESH_PART_ID), any()))
                .thenReturn(samplePart("backrest"));

        mockMvc.perform(RestDocumentationRequestBuilders
                        .put("/api/assets/{assetId}/mesh-parts/{meshPartId}", ASSET_ID, MESH_PART_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "code": "backrest",
                                    "shader": "standard",
                                    "material": "oak"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("backrest"))
                .andDo(document("mesh-part-update",
                        pathParameters(
                                parameterWithName("assetId").description("Parent asset identifier (UUID)"),
                                parameterWithName("meshPartId").description("Mesh part identifier (UUID)")
                        ),
                        requestFields(
                                fieldWithPath("code").description("Mesh part code (unique per asset, max 64 characters)"),
                                fieldWithPath("shader").optional()
                                        .description("Optional shader name (max 255 characters)"),
                                fieldWithPath("material").optional()
                                        .description("Optional material name (max 255 characters)")
                        ),
                        responseFields(
                                fieldWithPath("id").description("Mesh part identifier"),
                                fieldWithPath("assetId").description("Parent asset identifier"),
                                fieldWithPath("code").description("Mesh part code"),
                                fieldWithPath("shader").description("Shader name"),
                                fieldWithPath("material").description("Material name"),
                                fieldWithPath("createdAt").description("Creation timestamp"),
                                fieldWithPath("updatedAt").description("Last modification timestamp")
                        )));
    }

    @Test
    @WithMockUser
    void update_whenMeshPartNotFound_returns404ProblemDetail() throws Exception {
        when(meshPartService.update(eq(ASSET_ID), eq(MESH_PART_ID), any()))
                .thenThrow(new ResourceNotFoundException("MeshPart", "id", MESH_PART_ID));

        mockMvc.perform(put("/api/assets/{assetId}/mesh-parts/{meshPartId}", ASSET_ID, MESH_PART_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "code": "seat"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    @WithMockUser
    void update_whenDuplicateCode_returns409ProblemDetail() throws Exception {
        when(meshPartService.update(eq(ASSET_ID), eq(MESH_PART_ID), any()))
                .thenThrow(new DuplicateResourceException("MeshPart", "code", "taken"));

        mockMvc.perform(put("/api/assets/{assetId}/mesh-parts/{meshPartId}", ASSET_ID, MESH_PART_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "code": "taken"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate Resource"));
    }

    // ---- DELETE /api/assets/{assetId}/mesh-parts/{meshPartId} ----

    @Test
    @WithMockUser
    void delete_returns204() throws Exception {
        mockMvc.perform(RestDocumentationRequestBuilders
                        .delete("/api/assets/{assetId}/mesh-parts/{meshPartId}", ASSET_ID, MESH_PART_ID)
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andDo(document("mesh-part-delete",
                        pathParameters(
                                parameterWithName("assetId").description("Parent asset identifier (UUID)"),
                                parameterWithName("meshPartId").description("Mesh part identifier (UUID)")
                        )));

        verify(meshPartService).delete(ASSET_ID, MESH_PART_ID);
    }

    @Test
    @WithMockUser
    void delete_whenMeshPartNotFound_returns404ProblemDetail() throws Exception {
        doThrow(new ResourceNotFoundException("MeshPart", "id", MESH_PART_ID))
                .when(meshPartService).delete(ASSET_ID, MESH_PART_ID);

        mockMvc.perform(delete("/api/assets/{assetId}/mesh-parts/{meshPartId}", ASSET_ID, MESH_PART_ID)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    @WithMockUser
    void delete_whenMeshPartInUse_returns409ProblemDetail() throws Exception {
        doThrow(new ResourceInUseException("MeshPart", "geometries"))
                .when(meshPartService).delete(ASSET_ID, MESH_PART_ID);

        mockMvc.perform(delete("/api/assets/{assetId}/mesh-parts/{meshPartId}", ASSET_ID, MESH_PART_ID)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Resource In Use"))
                .andExpect(jsonPath("$.detail").value("Cannot delete MeshPart because it has associated geometries"));
    }

    @Test
    void delete_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/assets/{assetId}/mesh-parts/{meshPartId}", ASSET_ID, MESH_PART_ID))
                .andExpect(status().isUnauthorized());
    }
}
