package com.adb4.rmlmanager.controller;

import com.adb4.rmlmanager.dto.response.AssetSummaryResponse;
import com.adb4.rmlmanager.entity.AppUser;
import com.adb4.rmlmanager.enums.AssetStatus;
import com.adb4.rmlmanager.enums.UserRole;
import com.adb4.rmlmanager.exception.DuplicateResourceException;
import com.adb4.rmlmanager.exception.ResourceNotFoundException;
import com.adb4.rmlmanager.repository.AppUserRepository;
import com.adb4.rmlmanager.security.AppUserPrincipal;
import com.adb4.rmlmanager.service.AssetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AssetController.class)
@AutoConfigureRestDocs
@Import(com.adb4.rmlmanager.security.SecurityConfig.class)  // real chain: CSRF disabled, HTTP Basic entry point
class AssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssetService assetService;

    @MockitoBean
    private AppUserRepository appUserRepository;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID CAT_ID = UUID.randomUUID();
    private static final UUID SUB_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.now();

    private AppUserPrincipal testPrincipal() {
        AppUser user = AppUser.builder()
                .id(USER_ID)
                .username("testuser")
                .password("encoded")
                .role(UserRole.USER)
                .build();
        return new AppUserPrincipal(user);
    }

    private AppUserPrincipal adminPrincipal() {
        AppUser user = AppUser.builder()
                .id(USER_ID)
                .username("admin")
                .password("encoded")
                .role(UserRole.ADMIN)
                .build();
        return new AppUserPrincipal(user);
    }

    private AssetSummaryResponse sampleAsset(String code, String title) {
        return new AssetSummaryResponse(
                ASSET_ID, code, title, AssetStatus.DRAFT, null,
                "Furniture", "Chairs", false, NOW, NOW);
    }

    // ---- GET /api/assets (listing) ----

    @Test
    void findAll_defaultListing_returns200() throws Exception {
        Page<AssetSummaryResponse> page = new PageImpl<>(
                List.of(sampleAsset("CHR001", "Oak Chair")));
        when(assetService.findAllVisible(eq(USER_ID), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/assets")
                        .with(user(testPrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("CHR001"))
                .andExpect(jsonPath("$.content[0].title").value("Oak Chair"));
    }

    @Test
    void findAll_withCategoryFilter_forwardsParam() throws Exception {
        when(assetService.findAllVisible(eq(USER_ID), eq(CAT_ID), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/assets")
                        .param("categoryId", CAT_ID.toString())
                        .with(user(testPrincipal())))
                .andExpect(status().isOk());

        verify(assetService).findAllVisible(USER_ID, CAT_ID, null, null, null, Pageable.ofSize(20));
    }

    @Test
    void findAll_withSubcategoryFilter_forwardsParam() throws Exception {
        when(assetService.findAllVisible(eq(USER_ID), isNull(), eq(SUB_ID), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/assets")
                        .param("subcategoryId", SUB_ID.toString())
                        .with(user(testPrincipal())))
                .andExpect(status().isOk());

        verify(assetService).findAllVisible(eq(USER_ID), isNull(), eq(SUB_ID), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void findAll_withStatusFilter_forwardsParam() throws Exception {
        when(assetService.findAllVisible(eq(USER_ID), isNull(), isNull(), eq(AssetStatus.PUBLISHED), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/assets")
                        .param("status", "PUBLISHED")
                        .with(user(testPrincipal())))
                .andExpect(status().isOk());

        verify(assetService).findAllVisible(eq(USER_ID), isNull(), isNull(), eq(AssetStatus.PUBLISHED), isNull(), any(Pageable.class));
    }

    @Test
    void findAll_withTitleSearch_forwardsParam() throws Exception {
        when(assetService.findAllVisible(eq(USER_ID), isNull(), isNull(), isNull(), eq("oak"), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/assets")
                        .param("q", "oak")
                        .with(user(testPrincipal())))
                .andExpect(status().isOk());

        verify(assetService).findAllVisible(eq(USER_ID), isNull(), isNull(), isNull(), eq("oak"), any(Pageable.class));
    }

    @Test
    void findAll_withAllFilters_forwardsAllParams() throws Exception {
        when(assetService.findAllVisible(
                eq(USER_ID), eq(CAT_ID), eq(SUB_ID), eq(AssetStatus.DRAFT), eq("chair"), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/assets")
                        .param("categoryId", CAT_ID.toString())
                        .param("subcategoryId", SUB_ID.toString())
                        .param("status", "DRAFT")
                        .param("q", "chair")
                        .with(user(testPrincipal())))
                .andExpect(status().isOk());

        verify(assetService).findAllVisible(
                eq(USER_ID), eq(CAT_ID), eq(SUB_ID), eq(AssetStatus.DRAFT), eq("chair"), any(Pageable.class));
    }

    @Test
    void findAll_withPaginationParams_forwardsPageable() throws Exception {
        when(assetService.findAllVisible(eq(USER_ID), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/assets")
                        .param("page", "2")
                        .param("size", "10")
                        .param("sort", "title,asc")
                        .with(user(testPrincipal())))
                .andExpect(status().isOk());

        verify(assetService).findAllVisible(eq(USER_ID), isNull(), isNull(), isNull(), isNull(), argThat(p ->
                p.getPageNumber() == 2
                        && p.getPageSize() == 10
                        && p.getSort().getOrderFor("title") != null
        ));
    }

    @Test
    void findAll_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/assets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void findAll_withInvalidStatusParam_returns400() throws Exception {
        mockMvc.perform(get("/api/assets")
                        .param("status", "INVALID")
                        .with(user(testPrincipal())))
                .andExpect(status().isBadRequest());
    }

    // ---- GET /api/assets/{code} ----

    @Test
    @WithMockUser
    void getByCode_returns200WithAsset() throws Exception {
        AssetSummaryResponse asset = sampleAsset("CHR001", "Oak Chair");
        when(assetService.findByCode("CHR001")).thenReturn(asset);

        mockMvc.perform(RestDocumentationRequestBuilders.get("/api/assets/{code}", "CHR001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CHR001"))
                .andExpect(jsonPath("$.title").value("Oak Chair"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.categoryName").value("Furniture"))
                .andExpect(jsonPath("$.subcategoryName").value("Chairs"))
                .andDo(document("asset-get-by-code",
                        pathParameters(
                                parameterWithName("code").description("Unique asset code")
                        ),
                        responseFields(
                                fieldWithPath("id").description("Asset identifier"),
                                fieldWithPath("code").description("Unique asset code"),
                                fieldWithPath("title").description("Asset title"),
                                fieldWithPath("status").description("Asset status (DRAFT or PUBLISHED)"),
                                fieldWithPath("preview").type(JsonFieldType.STRING).optional()
                                        .description("Preview image URL"),
                                fieldWithPath("categoryName").description("Parent category name"),
                                fieldWithPath("subcategoryName").description("Subcategory name"),
                                fieldWithPath("hasAnimation").description("Whether the asset contains animation"),
                                fieldWithPath("createdAt").description("Creation timestamp"),
                                fieldWithPath("updatedAt").description("Last modification timestamp")
                        )));
    }

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
    void getByCode_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/assets/CHR001"))
                .andExpect(status().isUnauthorized());
    }

    // ---- POST /api/assets ----

    @Test
    void create_returns201WithLocationHeader() throws Exception {
        AssetSummaryResponse created = sampleAsset("CHR001", "Oak Chair");
        when(assetService.create(any())).thenReturn(created);

        mockMvc.perform(post("/api/assets")
                        .with(user(testPrincipal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "code": "CHR001",
                                    "title": "Oak Chair",
                                    "subcategoryId": "%s",
                                    "hasAnimation": false
                                }
                                """.formatted(SUB_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/assets/CHR001")))
                .andExpect(jsonPath("$.code").value("CHR001"))
                .andExpect(jsonPath("$.title").value("Oak Chair"))
                .andDo(document("asset-create",
                        requestFields(
                                fieldWithPath("code").description("Unique asset code (max 32 characters)"),
                                fieldWithPath("title").description("Asset title"),
                                fieldWithPath("subcategoryId").description("Subcategory identifier"),
                                // absent from this payload, so the type cannot be inferred
                                fieldWithPath("description").type(JsonFieldType.STRING).optional()
                                        .description("Optional asset description"),
                                fieldWithPath("hasAnimation").description("Whether the asset contains animation")
                        ),
                        responseFields(
                                fieldWithPath("id").description("Generated asset identifier"),
                                fieldWithPath("code").description("Unique asset code"),
                                fieldWithPath("title").description("Asset title"),
                                fieldWithPath("status").description("Asset status (defaults to DRAFT)"),
                                fieldWithPath("preview").type(JsonFieldType.STRING).optional()
                                        .description("Preview image URL"),
                                fieldWithPath("categoryName").description("Parent category name"),
                                fieldWithPath("subcategoryName").description("Subcategory name"),
                                fieldWithPath("hasAnimation").description("Whether the asset contains animation"),
                                fieldWithPath("createdAt").description("Creation timestamp"),
                                fieldWithPath("updatedAt").description("Last modification timestamp")
                        )));
    }

    @Test
    void create_withAuthenticatedPrincipal_delegatesToService() throws Exception {
        // Verifies that the request reaches the service layer when an AppUserPrincipal
        // is in the security context. The @CreatedBy annotation on Asset.createdBy is
        // populated by JpaAuditingConfig.auditorAware(), which reads from this same
        // security context — so a successful round-trip here means the auditing
        // infrastructure has the principal it needs.
        AssetSummaryResponse created = sampleAsset("AUD001", "Audited Asset");
        when(assetService.create(any())).thenReturn(created);

        mockMvc.perform(post("/api/assets")
                        .with(user(testPrincipal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "code": "AUD001",
                                    "title": "Audited Asset",
                                    "subcategoryId": "%s",
                                    "hasAnimation": false
                                }
                                """.formatted(SUB_ID)))
                .andExpect(status().isCreated());

        verify(assetService).create(argThat(req ->
                "AUD001".equals(req.code()) && "Audited Asset".equals(req.title())));
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
    void create_whenCodeExceeds32Chars_returns400() throws Exception {
        String longCode = "A".repeat(33);
        mockMvc.perform(post("/api/assets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "code": "%s",
                                    "title": "Valid Title",
                                    "subcategoryId": "%s",
                                    "hasAnimation": false
                                }
                                """.formatted(longCode, SUB_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.code").exists());
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "code": "CHR001",
                                    "title": "Oak Chair",
                                    "subcategoryId": "%s",
                                    "hasAnimation": false
                                }
                                """.formatted(SUB_ID)))
                .andExpect(status().isUnauthorized());
    }

    // ---- PUT /api/assets/{id} ----

    @Test
    @WithMockUser
    void update_returns200WithUpdatedAsset() throws Exception {
        AssetSummaryResponse updated = new AssetSummaryResponse(
                ASSET_ID, "CHR001", "Updated Chair", AssetStatus.DRAFT, null,
                "Furniture", "Chairs", true, NOW, NOW);
        when(assetService.update(eq(ASSET_ID), any())).thenReturn(updated);

        mockMvc.perform(RestDocumentationRequestBuilders.put("/api/assets/{id}", ASSET_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Updated Chair",
                                    "subcategoryId": "%s",
                                    "description": "Updated description",
                                    "hasAnimation": true,
                                    "status": "DRAFT"
                                }
                                """.formatted(SUB_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Chair"))
                .andExpect(jsonPath("$.hasAnimation").value(true))
                .andDo(document("asset-update",
                        pathParameters(
                                parameterWithName("id").description("Asset identifier (UUID)")
                        ),
                        requestFields(
                                fieldWithPath("title").description("Asset title"),
                                fieldWithPath("subcategoryId").description("Subcategory identifier"),
                                fieldWithPath("description").type(JsonFieldType.STRING).optional()
                                        .description("Optional asset description"),
                                fieldWithPath("hasAnimation").description("Whether the asset contains animation"),
                                fieldWithPath("status").description("Asset status (DRAFT or PUBLISHED)")
                        ),
                        responseFields(
                                fieldWithPath("id").description("Asset identifier"),
                                fieldWithPath("code").description("Unique asset code"),
                                fieldWithPath("title").description("Asset title"),
                                fieldWithPath("status").description("Asset status"),
                                fieldWithPath("preview").type(JsonFieldType.STRING).optional()
                                        .description("Preview image URL"),
                                fieldWithPath("categoryName").description("Parent category name"),
                                fieldWithPath("subcategoryName").description("Subcategory name"),
                                fieldWithPath("hasAnimation").description("Whether the asset contains animation"),
                                fieldWithPath("createdAt").description("Creation timestamp"),
                                fieldWithPath("updatedAt").description("Last modification timestamp")
                        )));
    }

    @Test
    @WithMockUser
    void update_whenAssetNotFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(assetService.update(eq(missingId), any()))
                .thenThrow(new ResourceNotFoundException("Asset", "id", missingId));

        mockMvc.perform(put("/api/assets/{id}", missingId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Updated",
                                    "subcategoryId": "%s",
                                    "hasAnimation": false,
                                    "status": "DRAFT"
                                }
                                """.formatted(SUB_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    @WithMockUser
    void update_whenSubcategoryNotFound_returns404ProblemDetail() throws Exception {
        UUID missingSubcategoryId = UUID.randomUUID();

        when(assetService.update(any(UUID.class), any()))
                .thenThrow(new ResourceNotFoundException("Subcategory", "id", missingSubcategoryId));

        mockMvc.perform(put("/api/assets/{id}", ASSET_ID)
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
    void update_whenValidationFails_returns400() throws Exception {
        mockMvc.perform(put("/api/assets/{id}", ASSET_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "",
                                    "subcategoryId": null,
                                    "hasAnimation": false,
                                    "status": "DRAFT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isMap());
    }

    @Test
    void update_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/assets/{id}", ASSET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Updated",
                                    "subcategoryId": "%s",
                                    "hasAnimation": false,
                                    "status": "DRAFT"
                                }
                                """.formatted(SUB_ID)))
                .andExpect(status().isUnauthorized());
    }

    // ---- error handling ----

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