package com.adb4.rmlmanager.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Upload limits, bound from the {@code storage.upload.*} namespace.
 *
 * <p>Mirrors the style of {@link S3Properties}: a plain
 * {@code @ConfigurationProperties} bean enabled via
 * {@code @EnableConfigurationProperties} in {@link S3ClientConfig}.
 */
@ConfigurationProperties(prefix = "storage.upload")
@Getter
@Setter
public class UploadProperties {

    /**
     * Maximum accepted size for a single geometry file. Requests whose declared
     * size exceeds this are rejected with 413 before anything is streamed to S3.
     * Keep this at or below {@code spring.servlet.multipart.max-file-size}, which
     * is the servlet-container backstop.
     */
    private DataSize maxGeometrySize = DataSize.ofMegabytes(512);
}
