package com.adb4.rmlmanager.service;

import com.adb4.rmlmanager.config.S3Properties;
import org.junit.jupiter.api.*;
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
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class S3StorageServiceIntegrationTest {

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

    private static S3StorageService storageService;

    @BeforeAll
    static void setUp() {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);

        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)
        );

        S3Configuration serviceConfig = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        S3Client s3Client = S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(endpoint))
                .serviceConfiguration(serviceConfig)
                .credentialsProvider(credentials)
                .build();

        s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());

        S3Presigner presigner = S3Presigner.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(endpoint))
                .serviceConfiguration(serviceConfig)
                .credentialsProvider(credentials)
                .build();

        S3Properties properties = new S3Properties();
        properties.setRegion("us-east-1");
        properties.setEndpoint(endpoint);
        properties.setPathStyleAccess(true);
        properties.setBucket(BUCKET);
        properties.setPresignedUrlExpiration(Duration.ofMinutes(5));
        properties.setAccessKey(ACCESS_KEY);
        properties.setSecretKey(SECRET_KEY);

        storageService = new S3StorageService(s3Client, presigner, properties);
    }

    @Test
    void putAndExistsRoundTrip() {
        String key = "geometry/chair-01/lod0/chair.glb";
        byte[] content = "binary-geometry-data".getBytes(StandardCharsets.UTF_8);

        storageService.put(key, new ByteArrayInputStream(content), content.length, "model/gltf-binary");

        assertTrue(storageService.exists(key));
    }

    @Test
    void existsReturnsFalseForMissingKey() {
        assertFalse(storageService.exists("nonexistent/key.bin"));
    }

    @Test
    void presignedGetUrlReturnsUploadedContent() throws Exception {
        String key = "texture/chair-01/variant/oak/lod0/albedo/albedo.png";
        byte[] content = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);

        storageService.put(key, new ByteArrayInputStream(content), content.length, "image/png");

        String url = storageService.presignedGetUrl(key);
        assertNotNull(url);

        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<byte[]> response = http.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );

        assertEquals(200, response.statusCode());
        assertArrayEquals(content, response.body());
    }

    @Test
    void deleteRemovesObject() {
        String key = "geometry/table-02/lod1/table.fbx";
        byte[] content = "delete-me".getBytes(StandardCharsets.UTF_8);

        storageService.put(key, new ByteArrayInputStream(content), content.length, "application/octet-stream");
        assertTrue(storageService.exists(key));

        storageService.delete(key);
        assertFalse(storageService.exists(key));
    }

    @Test
    void deleteNonexistentKeyDoesNotThrow() {
        assertDoesNotThrow(() -> storageService.delete("no-such/key.bin"));
    }

    @Test
    void presignedGetUrlRespectsCustomExpiration() {
        String key = "geometry/lamp-03/lod0/lamp.obj";
        byte[] content = "obj-data".getBytes(StandardCharsets.UTF_8);

        storageService.put(key, new ByteArrayInputStream(content), content.length, "text/plain");

        String url = storageService.presignedGetUrl(key, Duration.ofSeconds(30));
        assertNotNull(url);
        // URL contains an expiry parameter; just verify it was generated without error.
        assertTrue(url.contains("X-Amz-Expires="));
    }
}