package com.adb4.rmlmanager.controller;

import com.adb4.rmlmanager.TestcontainersConfiguration;
import com.adb4.rmlmanager.entity.AppUser;
import com.adb4.rmlmanager.entity.Asset;
import com.adb4.rmlmanager.entity.Geometry;
import com.adb4.rmlmanager.entity.Lod;
import com.adb4.rmlmanager.entity.MeshPart;
import com.adb4.rmlmanager.entity.Category;
import com.adb4.rmlmanager.entity.Subcategory;
import com.adb4.rmlmanager.enums.AssetStatus;
import com.adb4.rmlmanager.enums.GeometryFileType;
import com.adb4.rmlmanager.enums.UserRole;
import com.adb4.rmlmanager.repository.AppUserRepository;
import com.adb4.rmlmanager.repository.AssetRepository;
import com.adb4.rmlmanager.repository.CategoryRepository;
import com.adb4.rmlmanager.repository.GeometryRepository;
import com.adb4.rmlmanager.repository.LodRepository;
import com.adb4.rmlmanager.repository.MeshPartRepository;
import com.adb4.rmlmanager.repository.SubcategoryRepository;
import com.adb4.rmlmanager.security.AppUserPrincipal;
import com.adb4.rmlmanager.service.GeometryService;
import com.adb4.rmlmanager.service.StorageService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
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

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration test for KAN-21: Postgres via the shared
 * {@link TestcontainersConfiguration} and S3 via a MinIO container, mirroring
 * the setup proven in {@code S3StorageServiceIntegrationTest}.
 *
 * <p>The size limits are shrunk for testability: the application cap
 * ({@code storage.upload.max-geometry-size}) to 1KB and the servlet backstop
 * ({@code spring.servlet.multipart.max-file-size}) to 2KB. A file between the
 * two exercises the application check; a file over both exercises the servlet
 * backstop (which requires a real HTTP request, because MockMvc bypasses
 * multipart parsing and its limits).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "storage.upload.max-geometry-size=1KB",
                "spring.servlet.multipart.max-file-size=2KB"
        })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Testcontainers
class GeometryUploadIntegrationTest {

    private static final String BUCKET = "rml-assets";
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";
    private static final String RAW_PASSWORD = "integration-pw";
    private static final String BOUNDARY = "kan21TestBoundary";

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
    @Autowired private Environment environment;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private SubcategoryRepository subcategoryRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private LodRepository lodRepository;
    @Autowired private MeshPartRepository meshPartRepository;
    @Autowired private GeometryRepository geometryRepository;
    @Autowired private GeometryService geometryService;
    @Autowired private StorageService storageService;

    private AppUser user;
    private AppUserPrincipal principal;
    private Asset asset;
    private Lod lod;
    private MeshPart meshPart;

    /**
     * Each test gets its own user, asset, LOD, and mesh part with unique codes,
     * so tests stay independent without cross-test cleanup. Fixture saves run
     * with an authenticated security context because {@code Asset.createdBy} is
     * a non-null {@code @CreatedBy} column; the context is cleared afterwards so
     * each test controls its own authentication.
     */
    @BeforeEach
    void setUpFixtures() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user = appUserRepository.save(AppUser.builder()
                .username("up-" + suffix)
                .password(passwordEncoder.encode(RAW_PASSWORD))
                .role(UserRole.USER)
                .build());
        principal = new AppUserPrincipal(user);
        runAs(principal);
        try {
            asset = createAsset("chair-" + suffix);
            lod = lodRepository.save(Lod.builder().asset(asset).level(0).build());
            meshPart = meshPartRepository.save(MeshPart.builder().asset(asset).code("seat").build());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void uploadsFirstVersion() throws Exception {
        byte[] content = "glb-bytes-v1".getBytes(StandardCharsets.UTF_8);

        MvcResult result = mockMvc.perform(multipart("/api/lods/{lodId}/geometries", lod.getId())
                        .file(glbFile("chair.glb", content))
                        .param("fileType", "GLB")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lodId").value(lod.getId().toString()))
                .andExpect(jsonPath("$.meshPartId").value(nullValue()))
                .andExpect(jsonPath("$.fileName").value("chair.glb"))
                .andExpect(jsonPath("$.fileType").value("GLB"))
                .andExpect(jsonPath("$.fileSize").value(content.length))
                .andExpect(jsonPath("$.contentType").value("model/gltf-binary"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.isLatest").value(true))
                .andExpect(jsonPath("$.checksum").value(sha256Hex(content)))
                .andExpect(jsonPath("$.uploadedBy").value(user.getId().toString()))
                // internal storage coordinates must not leak into the API
                .andExpect(jsonPath("$.s3Key").doesNotExist())
                .andExpect(jsonPath("$.s3Bucket").doesNotExist())
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        String location = result.getResponse().getHeader("Location");
        assertNotNull(location);
        assertTrue(location.endsWith("/api/geometries/" + id));

        Geometry saved = geometryRepository.findById(UUID.fromString(id)).orElseThrow();
        assertEquals("geometry/" + asset.getCode() + "/lod0/v1/chair.glb", saved.getS3Key());
        assertEquals(BUCKET, saved.getS3Bucket());
        assertEquals(user.getId(), saved.getUploadedBy());
        assertTrue(storageService.exists(saved.getS3Key()));
    }

    @Test
    void secondUploadIncrementsVersionAndFlipsLatest() throws Exception {
        String id1 = uploadBaked("chair.glb", "glb-bytes-v1".getBytes(StandardCharsets.UTF_8));
        String id2 = uploadBaked("chair.glb", "glb-bytes-v2".getBytes(StandardCharsets.UTF_8));

        Geometry first = geometryRepository.findById(UUID.fromString(id1)).orElseThrow();
        Geometry second = geometryRepository.findById(UUID.fromString(id2)).orElseThrow();

        assertEquals(1, first.getVersion());
        assertFalse(first.getIsLatest());
        assertEquals(2, second.getVersion());
        assertTrue(second.getIsLatest());

        // The v1 segment keeps every version addressable: both objects exist.
        assertTrue(storageService.exists(first.getS3Key()));
        assertTrue(storageService.exists(second.getS3Key()));
        assertNotEquals(first.getS3Key(), second.getS3Key());
    }

    @Test
    void uploadWithMeshPartUsesPartKeyAndOwnVersionSequence() throws Exception {
        // A baked upload first: the mesh-part sequence must not be affected by it.
        uploadBaked("chair.glb", "baked-bytes".getBytes(StandardCharsets.UTF_8));

        byte[] content = "part-bytes".getBytes(StandardCharsets.UTF_8);
        MvcResult result = mockMvc.perform(multipart("/api/lods/{lodId}/geometries", lod.getId())
                        .file(glbFile("seat.glb", content))
                        .param("fileType", "GLB")
                        .param("meshPartId", meshPart.getId().toString())
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.meshPartId").value(meshPart.getId().toString()))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.isLatest").value(true))
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        Geometry saved = geometryRepository.findById(UUID.fromString(id)).orElseThrow();
        assertEquals("geometry/" + asset.getCode() + "/lod0/part/seat/v1/seat.glb", saved.getS3Key());
        assertTrue(storageService.exists(saved.getS3Key()));
    }

    @Test
    void rejectsUnknownExtensionWith400() throws Exception {
        mockMvc.perform(multipart("/api/lods/{lodId}/geometries", lod.getId())
                        .file(glbFile("chair.stp", "bytes".getBytes(StandardCharsets.UTF_8)))
                        .param("fileType", "GLB")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));

        assertTrue(geometryRepository.findByLodIdOrderByVersionDesc(lod.getId()).isEmpty());
    }

    @Test
    void rejectsMismatchedExtensionWith400() throws Exception {
        mockMvc.perform(multipart("/api/lods/{lodId}/geometries", lod.getId())
                        .file(glbFile("chair.obj", "bytes".getBytes(StandardCharsets.UTF_8)))
                        .param("fileType", "GLB")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));

        assertTrue(geometryRepository.findByLodIdOrderByVersionDesc(lod.getId()).isEmpty());
        assertFalse(storageService.exists("geometry/" + asset.getCode() + "/lod0/v1/chair.obj"));
    }

    @Test
    void rejectsOversizeWith413() throws Exception {
        // 1.5KB is over the 1KB application cap but under the 2KB servlet
        // backstop, so this exercises the PayloadTooLargeException path.
        byte[] content = new byte[1536];

        mockMvc.perform(multipart("/api/lods/{lodId}/geometries", lod.getId())
                        .file(glbFile("big.glb", content))
                        .param("fileType", "GLB")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.title").value("Payload Too Large"));

        assertTrue(geometryRepository.findByLodIdOrderByVersionDesc(lod.getId()).isEmpty());
        assertFalse(storageService.exists("geometry/" + asset.getCode() + "/lod0/v1/big.glb"));
    }

    /**
     * The servlet backstop only triggers during real multipart parsing, which
     * MockMvc skips, so this test posts over live HTTP to the random port.
     */
    @Test
    void servletBackstopRejectsOversizeWith413() throws Exception {
        byte[] body = multipartBody("huge.glb", new byte[3072]);
        int port = Integer.parseInt(environment.getProperty("local.server.port", "0"));
        String basic = Base64.getEncoder().encodeToString(
                (user.getUsername() + ":" + RAW_PASSWORD).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/lods/" + lod.getId() + "/geometries"))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .header("Authorization", "Basic " + basic)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(413, response.statusCode());
    }

    @Test
    void unknownLodReturns404() throws Exception {
        mockMvc.perform(multipart("/api/lods/{lodId}/geometries", UUID.randomUUID())
                        .file(glbFile("chair.glb", "bytes".getBytes(StandardCharsets.UTF_8)))
                        .param("fileType", "GLB")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    void meshPartFromAnotherAssetReturns404() throws Exception {
        runAs(principal);
        MeshPart foreign;
        try {
            Asset other = createAsset("table-" + UUID.randomUUID().toString().substring(0, 8));
            foreign = meshPartRepository.save(MeshPart.builder().asset(other).code("leg").build());
        } finally {
            SecurityContextHolder.clearContext();
        }

        mockMvc.perform(multipart("/api/lods/{lodId}/geometries", lod.getId())
                        .file(glbFile("chair.glb", "bytes".getBytes(StandardCharsets.UTF_8)))
                        .param("fileType", "GLB")
                        .param("meshPartId", foreign.getId().toString())
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));

        assertTrue(geometryRepository.findByLodIdOrderByVersionDesc(lod.getId()).isEmpty());
    }

    @Test
    void unauthenticatedUploadReturns401() throws Exception {
        mockMvc.perform(multipart("/api/lods/{lodId}/geometries", lod.getId())
                        .file(glbFile("chair.glb", "bytes".getBytes(StandardCharsets.UTF_8)))
                        .param("fileType", "GLB")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Compensation: the S3 put runs before the row is persisted, so a failure
     * at commit must delete the just-written object. Calling the service
     * directly with an empty security context leaves {@code @CreatedBy}
     * unresolved; the not-null {@code uploaded_by} column then fails the
     * commit-time flush, after the put has already succeeded. The registered
     * synchronization must observe the rollback and delete the orphan.
     */
    @Test
    void rollsBackAndDeletesOrphanOnCommitFailure() {
        MockMultipartFile file = glbFile("chair.glb", "orphan-bytes".getBytes(StandardCharsets.UTF_8));

        assertThrows(RuntimeException.class,
                () -> geometryService.upload(lod.getId(), GeometryFileType.GLB, null, file));

        assertFalse(storageService.exists("geometry/" + asset.getCode() + "/lod0/v1/chair.glb"));
        assertTrue(geometryRepository.findByLodIdOrderByVersionDesc(lod.getId()).isEmpty());
    }

    private String uploadBaked(String fileName, byte[] content) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/lods/{lodId}/geometries", lod.getId())
                        .file(glbFile(fileName, content))
                        .param("fileType", "GLB")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private MockMultipartFile glbFile(String fileName, byte[] content) {
        return new MockMultipartFile("file", fileName, "model/gltf-binary", content);
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

    private static byte[] multipartBody(String fileName, byte[] content) {
        String head = "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"fileType\"\r\n\r\nGLB\r\n"
                + "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: model/gltf-binary\r\n\r\n";
        String tail = "\r\n--" + BOUNDARY + "--\r\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(head.getBytes(StandardCharsets.UTF_8));
        out.writeBytes(content);
        out.writeBytes(tail.getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}
