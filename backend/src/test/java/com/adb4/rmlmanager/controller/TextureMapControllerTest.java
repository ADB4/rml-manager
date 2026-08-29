package com.adb4.rmlmanager.controller;

import com.adb4.rmlmanager.dto.response.TextureMapResponse;
import com.adb4.rmlmanager.enums.TextureFileType;
import com.adb4.rmlmanager.enums.TextureMapType;
import com.adb4.rmlmanager.exception.DuplicateResourceException;
import com.adb4.rmlmanager.exception.InvalidUploadException;
import com.adb4.rmlmanager.exception.ResourceNotFoundException;
import com.adb4.rmlmanager.repository.AppUserRepository;
import com.adb4.rmlmanager.service.TextureMapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TextureMapController.class)
@AutoConfigureRestDocs
@Import(com.adb4.rmlmanager.security.SecurityConfig.class)  // real chain: CSRF disabled, HTTP Basic entry point
class TextureMapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TextureMapService textureMapService;

    @MockitoBean
    private AppUserRepository appUserRepository;

    private static final UUID SET_ID = UUID.randomUUID();
    private static final UUID MAP_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String CHECKSUM = "a".repeat(64);
    private static final LocalDateTime NOW = LocalDateTime.now();

    private TextureMapResponse sampleMap() {
        return new TextureMapResponse(MAP_ID, TextureMapType.ALBEDO, "albedo.png", TextureFileType.PNG,
                512, 1024, 1024, CHECKSUM, USER_ID, NOW, NOW);
    }

    private MockMultipartFile pngPart() {
        return new MockMultipartFile("file", "albedo.png", "image/png",
                "png-bytes".getBytes(StandardCharsets.UTF_8));
    }

    // ---- POST /api/texture-sets/{textureSetId}/maps ----

    @Test
    @WithMockUser
    void upload_returns201WithLocationHeader() throws Exception {
        when(textureMapService.upload(eq(SET_ID), eq(TextureMapType.ALBEDO), any())).thenReturn(sampleMap());

        mockMvc.perform(RestDocumentationRequestBuilders.multipart("/api/texture-sets/{textureSetId}/maps", SET_ID)
                        .file(pngPart())
                        .queryParam("type", "ALBEDO")
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/texture-maps/" + MAP_ID)))
                .andExpect(jsonPath("$.id").value(MAP_ID.toString()))
                .andExpect(jsonPath("$.type").value("ALBEDO"))
                .andExpect(jsonPath("$.fileName").value("albedo.png"))
                .andExpect(jsonPath("$.fileType").value("PNG"))
                .andExpect(jsonPath("$.width").value(1024))
                .andExpect(jsonPath("$.height").value(1024))
                .andExpect(jsonPath("$.checksum").value(CHECKSUM))
                // internal storage coordinates must not leak into the API
                .andExpect(jsonPath("$.s3Key").doesNotExist())
                .andExpect(jsonPath("$.s3Bucket").doesNotExist())
                .andDo(document("texture-map-upload",
                        pathParameters(
                                parameterWithName("textureSetId").description("Target texture set identifier (UUID)")
                        ),
                        queryParameters(
                                parameterWithName("type").description("Texture map type (ALBEDO, NORMAL, ROUGHNESS, METALLIC, AO, DISPLACEMENT, EMISSIVE, OPACITY)")
                        ),
                        requestParts(
                                partWithName("file").description("Texture image; the extension determines the file type and must be a supported texture format")
                        ),
                        responseFields(
                                fieldWithPath("id").description("Texture map identifier (an existing map's id when content was deduplicated)"),
                                fieldWithPath("type").description("Texture map type"),
                                fieldWithPath("fileName").description("Original file name"),
                                fieldWithPath("fileType").description("Texture file type derived from the extension"),
                                fieldWithPath("fileSize").description("File size in bytes"),
                                fieldWithPath("width").description("Pixel width, or null when the format has no dimension reader"),
                                fieldWithPath("height").description("Pixel height, or null when the format has no dimension reader"),
                                fieldWithPath("checksum").description("SHA-256 checksum used for deduplication"),
                                fieldWithPath("uploadedBy").description("Identifier of the user who first uploaded the content"),
                                fieldWithPath("createdAt").description("Creation timestamp"),
                                fieldWithPath("updatedAt").description("Last modification timestamp")
                        )));
    }

    @Test
    @WithMockUser
    void upload_whenExtensionUnsupported_returns400ProblemDetail() throws Exception {
        when(textureMapService.upload(eq(SET_ID), eq(TextureMapType.ALBEDO), any()))
                .thenThrow(new InvalidUploadException("Unsupported texture extension: .bmp"));

        mockMvc.perform(multipart("/api/texture-sets/{textureSetId}/maps", SET_ID)
                        .file(pngPart())
                        .param("type", "ALBEDO")
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Unsupported texture extension: .bmp"));
    }

    @Test
    @WithMockUser
    void upload_whenMapAlreadyInSet_returns409ProblemDetail() throws Exception {
        when(textureMapService.upload(eq(SET_ID), eq(TextureMapType.ALBEDO), any()))
                .thenThrow(new DuplicateResourceException("TextureMap", "checksum", CHECKSUM));

        mockMvc.perform(multipart("/api/texture-sets/{textureSetId}/maps", SET_ID)
                        .file(pngPart())
                        .param("type", "ALBEDO")
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate Resource"));
    }

    @Test
    @WithMockUser
    void upload_whenSetNotFound_returns404ProblemDetail() throws Exception {
        when(textureMapService.upload(eq(SET_ID), eq(TextureMapType.ALBEDO), any()))
                .thenThrow(new ResourceNotFoundException("TextureSet", "id", SET_ID));

        mockMvc.perform(multipart("/api/texture-sets/{textureSetId}/maps", SET_ID)
                        .file(pngPart())
                        .param("type", "ALBEDO")
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    void upload_unauthenticated_returns401() throws Exception {
        mockMvc.perform(multipart("/api/texture-sets/{textureSetId}/maps", SET_ID)
                        .file(pngPart())
                        .param("type", "ALBEDO"))
                .andExpect(status().isUnauthorized());
    }

    // ---- DELETE /api/texture-sets/{textureSetId}/maps/{textureMapId} ----

    @Test
    @WithMockUser
    void remove_returns204() throws Exception {
        mockMvc.perform(RestDocumentationRequestBuilders
                        .delete("/api/texture-sets/{textureSetId}/maps/{textureMapId}", SET_ID, MAP_ID)
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andDo(document("texture-map-remove",
                        pathParameters(
                                parameterWithName("textureSetId").description("Texture set identifier (UUID)"),
                                parameterWithName("textureMapId").description("Texture map identifier (UUID); only the association to this set is removed")
                        )));

        verify(textureMapService).removeFromSet(SET_ID, MAP_ID);
    }

    @Test
    @WithMockUser
    void remove_whenMapNotInSet_returns404ProblemDetail() throws Exception {
        doThrow(new ResourceNotFoundException("TextureMap", "id", MAP_ID))
                .when(textureMapService).removeFromSet(SET_ID, MAP_ID);

        mockMvc.perform(delete("/api/texture-sets/{textureSetId}/maps/{textureMapId}", SET_ID, MAP_ID)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    void remove_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/texture-sets/{textureSetId}/maps/{textureMapId}", SET_ID, MAP_ID))
                .andExpect(status().isUnauthorized());
    }
}
