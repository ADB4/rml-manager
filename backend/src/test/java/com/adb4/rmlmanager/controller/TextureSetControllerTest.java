package com.adb4.rmlmanager.controller;

import com.adb4.rmlmanager.dto.response.TextureSetResponse;
import com.adb4.rmlmanager.exception.ResourceNotFoundException;
import com.adb4.rmlmanager.repository.AppUserRepository;
import com.adb4.rmlmanager.service.TextureSetService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TextureSetController.class)
@AutoConfigureRestDocs
@Import(com.adb4.rmlmanager.security.SecurityConfig.class)  // real chain: CSRF disabled, HTTP Basic entry point
class TextureSetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TextureSetService textureSetService;

    @MockitoBean
    private AppUserRepository appUserRepository;

    private static final UUID VARIANT_ID = UUID.randomUUID();
    private static final UUID SET_ID = UUID.randomUUID();
    private static final UUID LOD_ID = UUID.randomUUID();
    private static final UUID MESH_PART_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.now();

    // ---- POST /api/variants/{variantId}/texture-sets ----

    @Test
    @WithMockUser
    void create_returns201WithTextureSet() throws Exception {
        TextureSetResponse response = new TextureSetResponse(
                SET_ID, VARIANT_ID, LOD_ID, MESH_PART_ID, 1, true, List.of(), NOW, NOW);
        when(textureSetService.create(eq(VARIANT_ID), any())).thenReturn(response);

        mockMvc.perform(RestDocumentationRequestBuilders.post("/api/variants/{variantId}/texture-sets", VARIANT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "lodId": "%s",
                                    "meshPartId": "%s"
                                }
                                """.formatted(LOD_ID, MESH_PART_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(SET_ID.toString()))
                .andExpect(jsonPath("$.variantId").value(VARIANT_ID.toString()))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.isLatest").value(true))
                .andDo(document("texture-set-create",
                        pathParameters(
                                parameterWithName("variantId").description("Parent variant identifier (UUID)")
                        ),
                        requestFields(
                                fieldWithPath("lodId").optional()
                                        .description("Optional LOD to scope the set to; null means shared across all LOD levels"),
                                fieldWithPath("meshPartId").optional()
                                        .description("Optional mesh part to scope the set to; null means whole-asset geometry")
                        ),
                        responseFields(
                                fieldWithPath("id").description("Generated texture set identifier"),
                                fieldWithPath("variantId").description("Parent variant identifier"),
                                fieldWithPath("lodId").description("Scoping LOD identifier, or null"),
                                fieldWithPath("meshPartId").description("Scoping mesh part identifier, or null"),
                                fieldWithPath("version").description("Version within the (variant, lod, meshPart) scope"),
                                fieldWithPath("isLatest").description("Whether this set is the latest version in its scope"),
                                fieldWithPath("maps").description("Texture maps in the set (empty on creation)"),
                                fieldWithPath("createdAt").description("Creation timestamp"),
                                fieldWithPath("updatedAt").description("Last modification timestamp")
                        )));
    }

    @Test
    @WithMockUser
    void create_withEmptyBody_returns201ForUnscopedSet() throws Exception {
        TextureSetResponse response = new TextureSetResponse(
                SET_ID, VARIANT_ID, null, null, 1, true, List.of(), NOW, NOW);
        when(textureSetService.create(eq(VARIANT_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/variants/{variantId}/texture-sets", VARIANT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lodId").doesNotExist())
                .andExpect(jsonPath("$.meshPartId").doesNotExist())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @WithMockUser
    void create_whenVariantNotFound_returns404ProblemDetail() throws Exception {
        when(textureSetService.create(eq(VARIANT_ID), any()))
                .thenThrow(new ResourceNotFoundException("Variant", "id", VARIANT_ID));

        mockMvc.perform(post("/api/variants/{variantId}/texture-sets", VARIANT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andExpect(jsonPath("$.detail").value("Variant not found with id: " + VARIANT_ID));
    }

    @Test
    @WithMockUser
    void create_whenLodNotFound_returns404ProblemDetail() throws Exception {
        when(textureSetService.create(eq(VARIANT_ID), any()))
                .thenThrow(new ResourceNotFoundException("Lod", "id", LOD_ID));

        mockMvc.perform(post("/api/variants/{variantId}/texture-sets", VARIANT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "lodId": "%s"
                                }
                                """.formatted(LOD_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/variants/{variantId}/texture-sets", VARIANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
