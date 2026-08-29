package com.adb4.rmlmanager.controller;

import com.adb4.rmlmanager.TestcontainersConfiguration;
import com.adb4.rmlmanager.entity.AppUser;
import com.adb4.rmlmanager.entity.Asset;
import com.adb4.rmlmanager.entity.Category;
import com.adb4.rmlmanager.entity.Lod;
import com.adb4.rmlmanager.entity.MeshPart;
import com.adb4.rmlmanager.entity.Subcategory;
import com.adb4.rmlmanager.entity.TextureMap;
import com.adb4.rmlmanager.entity.TextureSet;
import com.adb4.rmlmanager.entity.Variant;
import com.adb4.rmlmanager.enums.AssetStatus;
import com.adb4.rmlmanager.enums.TextureMapType;
import com.adb4.rmlmanager.enums.UserRole;
import com.adb4.rmlmanager.repository.AppUserRepository;
import com.adb4.rmlmanager.repository.AssetRepository;
import com.adb4.rmlmanager.repository.CategoryRepository;
import com.adb4.rmlmanager.repository.LodRepository;
import com.adb4.rmlmanager.repository.MeshPartRepository;
import com.adb4.rmlmanager.repository.SubcategoryRepository;
import com.adb4.rmlmanager.repository.TextureMapRepository;
import com.adb4.rmlmanager.repository.TextureSetRepository;
import com.adb4.rmlmanager.repository.VariantRepository;
import com.adb4.rmlmanager.security.AppUserPrincipal;
import com.adb4.rmlmanager.service.StorageService;
import com.adb4.rmlmanager.service.TextureMapService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration test for KAN-22: Postgres via the shared
 * {@link TestcontainersConfiguration} and S3 via a MinIO container, mirroring
 * {@code GeometryUploadIntegrationTest}.
 *
 * <p>The texture size cap is shrunk to 1KB so the 413 path is testable with a
 * small payload; the tiny generated PNGs used elsewhere stay well under it.
 * The servlet backstop is already covered by the geometry suite and is not
 * repeated here.
 */
@SpringBootTest(properties = "storage.upload.max-texture-size=1KB")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Testcontainers
class TextureUploadIntegrationTest {

    private static final String BUCKET = "rml-assets";
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";

    @Container
    static final GenericContainer<?> MINIO =
            new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
                    .withExposedPorts(9000)
                    .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
                    .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
                    .withCommand("server", "/data");

    @DynamicPropertySource
    static void s3Endpoint(DynamicPropertyRegistry registry) {
        registry.add("storage.s3.endpoint",
                () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
    }

    @BeforeAll
    static void createBucket() {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        try (S3Client client = S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(endpoint))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .build()) {
            client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SubcategoryRepository subcategoryRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private LodRepository lodRepository;
    @Autowired private MeshPartRepository meshPartRepository;
    @Autowired private VariantRepository variantRepository;
    @Autowired private TextureSetRepository textureSetRepository;
    @Autowired private TextureMapRepository textureMapRepository;
    @Autowired private TextureMapService textureMapService;
    @Autowired private StorageService storageService;

    private AppUser user;
    private AppUserPrincipal principal;
    private Asset asset;
    private Lod lod;
    private MeshPart meshPart;
    private Variant variant;

    /**
     * Each test gets its own user, asset, LOD, mesh part, and variant with
     * unique codes, so tests stay independent without cross-test cleanup.
     * Fixture saves run with an authenticated security context because
     * {@code Asset.createdBy} is a non-null {@code @CreatedBy} column and the
     * Envers-audited entities record the acting user; the context is cleared
     * afterwards so each test controls its own authentication.
     */
    @BeforeEach
    void setUpFixtures() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user = appUserRepository.save(AppUser.builder()
                .username("tx-" + suffix)
                .password(passwordEncoder.encode("integration-pw"))
                .role(UserRole.USER)
                .build());
        principal = new AppUserPrincipal(user);
        runAs(principal);
        try {
            asset = createAsset("chair-" + suffix);
            lod = lodRepository.save(Lod.builder().asset(asset).level(0).build());
            meshPart = meshPartRepository.save(MeshPart.builder().asset(asset).code("seat").build());
            variant = variantRepository.save(Variant.builder()
                    .asset(asset)
                    .code("oak")
                    .displayName("Oak")
                    .build());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ---- texture set creation and versioning ----

    @Test
    void createsFirstSetInScope() throws Exception {
        mockMvc.perform(post("/api/variants/{variantId}/texture-sets", variant.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.variantId").value(variant.getId().toString()))
                .andExpect(jsonPath("$.lodId").value(nullValue()))
                .andExpect(jsonPath("$.meshPartId").value(nullValue()))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.isLatest").value(true))
                .andExpect(jsonPath("$.maps").isEmpty());
    }

    @Test
    void secondSetInSameScopeIncrementsVersionAndFlipsLatest() throws Exception {
        UUID firstId = createSet("{}");
        UUID secondId = createSet("{}");

        TextureSet first = textureSetRepository.findById(firstId).orElseThrow();
        TextureSet second = textureSetRepository.findById(secondId).orElseThrow();

        assertEquals(1, first.getVersion());
        assertFalse(first.getIsLatest());
        assertEquals(2, second.getVersion());
        assertTrue(second.getIsLatest());
    }

    @Test
    void scopedSetsHaveTheirOwnVersionSequences() throws Exception {
        createSet("{}");

        // A LOD-and-mesh-part-scoped set is a different scope: it starts at v1
        // and the unscoped set stays latest in its own scope.
        mockMvc.perform(post("/api/variants/{variantId}/texture-sets", variant.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "lodId": "%s",
                                    "meshPartId": "%s"
                                }
                                """.formatted(lod.getId(), meshPart.getId()))
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lodId").value(lod.getId().toString()))
                .andExpect(jsonPath("$.meshPartId").value(meshPart.getId().toString()))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.isLatest").value(true));

        boolean unscopedStillLatest = textureSetRepository.findByVariantId(variant.getId()).stream()
                .filter(set -> set.getLod() == null && set.getMeshPart() == null)
                .allMatch(set -> Boolean.TRUE.equals(set.getIsLatest()));
        assertTrue(unscopedStillLatest);
    }

    @Test
    void scopeFromAnotherAssetReturns404() throws Exception {
        runAs(principal);
        Lod foreignLod;
        MeshPart foreignPart;
        try {
            Asset other = createAsset("table-" + UUID.randomUUID().toString().substring(0, 8));
            foreignLod = lodRepository.save(Lod.builder().asset(other).level(0).build());
            foreignPart = meshPartRepository.save(MeshPart.builder().asset(other).code("leg").build());
        } finally {
            SecurityContextHolder.clearContext();
        }

        mockMvc.perform(post("/api/variants/{variantId}/texture-sets", variant.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lodId\": \"%s\"}".formatted(foreignLod.getId()))
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));

        mockMvc.perform(post("/api/variants/{variantId}/texture-sets", variant.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meshPartId\": \"%s\"}".formatted(foreignPart.getId()))
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));

        assertTrue(textureSetRepository.findByVariantId(variant.getId()).isEmpty());
    }

    // ---- texture map upload ----

    @Test
    void uploadStoresMapWithDimensionsChecksumAndKey() throws Exception {
        byte[] content = pngBytes(4, 2);
        UUID setId = createSet("{}");

        MvcResult result = mockMvc.perform(multipart("/api/texture-sets/{textureSetId}/maps", setId)
                        .file(pngFile("albedo.png", content))
                        .param("type", "ALBEDO")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ALBEDO"))
                .andExpect(jsonPath("$.fileName").value("albedo.png"))
                .andExpect(jsonPath("$.fileType").value("PNG"))
                .andExpect(jsonPath("$.fileSize").value(content.length))
                .andExpect(jsonPath("$.width").value(4))
                .andExpect(jsonPath("$.height").value(2))
                .andExpect(jsonPath("$.checksum").value(sha256Hex(content)))
                .andExpect(jsonPath("$.uploadedBy").value(user.getId().toString()))
                // internal storage coordinates must not leak into the API
                .andExpect(jsonPath("$.s3Key").doesNotExist())
                .andExpect(jsonPath("$.s3Bucket").doesNotExist())
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertEquals("/api/texture-maps/" + id,
                URI.create(result.getResponse().getHeader("Location")).getPath());

        TextureMap saved = textureMapRepository.findById(UUID.fromString(id)).orElseThrow();
        assertEquals(unscopedKey("albedo.png", content), saved.getS3Key());
        assertEquals(BUCKET, saved.getS3Bucket());
        assertEquals(user.getId(), saved.getUploadedBy());
        assertTrue(storageService.exists(saved.getS3Key()));
    }

    @Test
    void uploadIntoScopedSetUsesLodAndPartKeySegments() throws Exception {
        byte[] content = pngBytes(2, 2);
        UUID setId = createSet("""
                {
                    "lodId": "%s",
                    "meshPartId": "%s"
                }
                """.formatted(lod.getId(), meshPart.getId()));

        String id = uploadMap(setId, "NORMAL", pngFile("normal.png", content));

        TextureMap saved = textureMapRepository.findById(UUID.fromString(id)).orElseThrow();
        assertEquals("texture/" + asset.getCode() + "/variant/oak/lod0/part/seat/normal/"
                + checksum16(content) + "/normal.png", saved.getS3Key());
        assertTrue(storageService.exists(saved.getS3Key()));
    }

    @Test
    void identicalUploadIntoAnotherSetLinksExistingMapWithoutNewObject() throws Exception {
        byte[] content = pngBytes(4, 2);
        UUID unscopedSetId = createSet("{}");
        UUID lodScopedSetId = createSet("{\"lodId\": \"%s\"}".formatted(lod.getId()));

        String firstId = uploadMap(unscopedSetId, "ALBEDO", pngFile("albedo.png", content));
        String secondId = uploadMap(lodScopedSetId, "ALBEDO", pngFile("albedo.png", content));

        // Same row linked, not a new one.
        assertEquals(firstId, secondId);

        // The object stays at the first uploader's key; no second object was
        // written under the LOD-scoped prefix.
        assertTrue(storageService.exists(unscopedKey("albedo.png", content)));
        assertFalse(storageService.exists("texture/" + asset.getCode() + "/variant/oak/lod0/albedo/"
                + checksum16(content) + "/albedo.png"));
    }

    @Test
    void identicalUploadIntoSameSetReturns409() throws Exception {
        byte[] content = pngBytes(4, 2);
        UUID setId = createSet("{}");
        uploadMap(setId, "ALBEDO", pngFile("albedo.png", content));

        mockMvc.perform(multipart("/api/texture-sets/{textureSetId}/maps", setId)
                        .file(pngFile("albedo.png", content))
                        .param("type", "ALBEDO")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate Resource"));
    }

    @Test
    void identicalUploadWithDifferentDeclaredTypeReturns400() throws Exception {
        byte[] content = pngBytes(4, 2);
        UUID setId = createSet("{}");
        UUID otherSetId = createSet("{\"lodId\": \"%s\"}".formatted(lod.getId()));
        uploadMap(setId, "ALBEDO", pngFile("albedo.png", content));

        mockMvc.perform(multipart("/api/texture-sets/{textureSetId}/maps", otherSetId)
                        .file(pngFile("albedo.png", content))
                        .param("type", "NORMAL")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    void unsupportedExtensionReturns400() throws Exception {
        UUID setId = createSet("{}");

        mockMvc.perform(multipart("/api/texture-sets/{textureSetId}/maps", setId)
                        .file(pngFile("albedo.bmp", "bytes".getBytes(StandardCharsets.UTF_8)))
                        .param("type", "ALBEDO")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));

        assertTrue(textureSetRepository.findById(setId).isPresent());
        assertTrue(textureMapRepository.findByChecksum(sha256Hex("bytes".getBytes(StandardCharsets.UTF_8))).isEmpty());
    }

    @Test
    void oversizeUploadReturns413() throws Exception {
        // 1.5KB is over the 1KB texture cap configured for this test context.
        byte[] content = new byte[1536];
        UUID setId = createSet("{}");

        mockMvc.perform(multipart("/api/texture-sets/{textureSetId}/maps", setId)
                        .file(pngFile("big.png", content))
                        .param("type", "ALBEDO")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.title").value("Payload Too Large"));

        assertTrue(textureMapRepository.findByChecksum(sha256Hex(content)).isEmpty());
    }

    @Test
    void formatWithoutImageReaderStoresNullDimensions() throws Exception {
        // A stock JDK has no WEBP reader, so dimension extraction is skipped.
        byte[] content = "not-actually-webp".getBytes(StandardCharsets.UTF_8);
        UUID setId = createSet("{}");

        mockMvc.perform(multipart("/api/texture-sets/{textureSetId}/maps", setId)
                        .file(pngFile("tex.webp", content))
                        .param("type", "ROUGHNESS")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileType").value("WEBP"))
                .andExpect(jsonPath("$.width").value(nullValue()))
                .andExpect(jsonPath("$.height").value(nullValue()));
    }

    @Test
    void uploadIntoUnknownSetReturns404() throws Exception {
        mockMvc.perform(multipart("/api/texture-sets/{textureSetId}/maps", UUID.randomUUID())
                        .file(pngFile("albedo.png", pngBytes(2, 2)))
                        .param("type", "ALBEDO")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    void unauthenticatedUploadReturns401() throws Exception {
        UUID setId = createSet("{}");

        mockMvc.perform(multipart("/api/texture-sets/{textureSetId}/maps", setId)
                        .file(pngFile("albedo.png", pngBytes(2, 2)))
                        .param("type", "ALBEDO")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ---- map removal ----

    @Test
    void removeDeletesAssociationOnlyAndKeepsRowAndObject() throws Exception {
        byte[] content = pngBytes(4, 2);
        UUID setA = createSet("{}");
        UUID setB = createSet("{\"lodId\": \"%s\"}".formatted(lod.getId()));
        UUID mapId = UUID.fromString(uploadMap(setA, "ALBEDO", pngFile("albedo.png", content)));
        uploadMap(setB, "ALBEDO", pngFile("albedo.png", content));
        String key = unscopedKey("albedo.png", content);

        mockMvc.perform(delete("/api/texture-sets/{textureSetId}/maps/{textureMapId}", setA, mapId)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // The association is gone (a second remove finds nothing) but the row
        // and object survive: setB still references them.
        mockMvc.perform(delete("/api/texture-sets/{textureSetId}/maps/{textureMapId}", setA, mapId)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isNotFound());
        assertTrue(textureMapRepository.findById(mapId).isPresent());
        assertTrue(storageService.exists(key));

        // Removal is association-only even for the last reference.
        mockMvc.perform(delete("/api/texture-sets/{textureSetId}/maps/{textureMapId}", setB, mapId)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        assertTrue(textureMapRepository.findById(mapId).isPresent());
        assertTrue(storageService.exists(key));
    }

    // ---- consistency compensation ----

    /**
     * Compensation: the S3 put runs before the row is persisted, so a failure
     * at commit must delete the just-written object. Calling the service
     * directly with an empty security context leaves {@code @CreatedBy}
     * unresolved; the not-null {@code uploaded_by} column then fails the
     * commit-time flush, after the put has already succeeded. The registered
     * synchronization must observe the rollback and delete the orphan.
     */
    @Test
    void rollsBackAndDeletesOrphanOnCommitFailure() throws Exception {
        byte[] content = pngBytes(4, 2);
        UUID setId = createSet("{}");
        MockMultipartFile file = pngFile("orphan.png", content);

        assertThrows(RuntimeException.class,
                () -> textureMapService.upload(setId, TextureMapType.ALBEDO, file));

        assertFalse(storageService.exists(unscopedKey("orphan.png", content)));
        assertTrue(textureMapRepository.findByChecksum(sha256Hex(content)).isEmpty());
    }

    // ---- helpers ----

    private UUID createSet(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/variants/{variantId}/texture-sets", variant.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return UUID.fromString(id);
    }

    private String uploadMap(UUID setId, String type, MockMultipartFile file) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/texture-sets/{textureSetId}/maps", setId)
                        .file(file)
                        .param("type", type)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    /** Key for a map uploaded into an unscoped set of this test's variant. */
    private String unscopedKey(String fileName, byte[] content) throws Exception {
        String mapType = fileName.startsWith("normal") ? "normal" : "albedo";
        return "texture/" + asset.getCode() + "/variant/oak/" + mapType + "/"
                + checksum16(content) + "/" + fileName;
    }

    private MockMultipartFile pngFile(String fileName, byte[] content) {
        return new MockMultipartFile("file", fileName, "image/png", content);
    }

    /**
     * Tiny PNG with known pixel dimensions and unique pixel content per call.
     * Deduplication is global and the tests share one database, so identical
     * bytes across tests would link another test's map (its user, its key)
     * instead of exercising the fresh-upload path.
     */
    private static byte[] pngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, ThreadLocalRandom.current().nextInt(1 << 24));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private Asset createAsset(String code) {
        Category category = categoryRepository.save(Category.builder()
                .name("cat-" + code)
                .build());
        Subcategory subcategory = subcategoryRepository.save(Subcategory.builder()
                .category(category)
                .name("sub-" + code)
                .build());
        return assetRepository.save(Asset.builder()
                .code(code)
                .title("Asset " + code)
                .subcategory(subcategory)
                .status(AssetStatus.DRAFT)
                .build());
    }

    private void runAs(AppUserPrincipal p) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, p.getPassword(), p.getAuthorities()));
    }

    private static String sha256Hex(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static String checksum16(byte[] content) throws Exception {
        return sha256Hex(content).substring(0, 16);
    }
}
