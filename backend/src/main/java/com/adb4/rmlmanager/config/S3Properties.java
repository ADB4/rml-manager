package com.adb4.rmlmanager.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "storage.s3")
@Getter
@Setter
public class S3Properties {

    /**
     * AWS region for the S3 client.
     */
    private String region = "us-east-1";

    /**
     * Optional endpoint override (e.g. {@code http://localhost:9000} for MinIO).
     * Left {@code null} for production to use the default AWS endpoint.
     */
    private String endpoint;

    /**
     * Enable path-style access ({@code http://host/bucket/key} instead of
     * {@code http://bucket.host/key}).  Required for MinIO and most S3-compatible
     * stores; leave {@code false} for real AWS S3.
     */
    private boolean pathStyleAccess = false;

    /**
     * Default bucket name for asset storage.
     */
    private String bucket = "rml-assets";

    /**
     * Default expiration for presigned GET URLs.
     */
    private Duration presignedUrlExpiration = Duration.ofHours(1);

    /**
     * Optional static access key.  When both {@code accessKey} and
     * {@code secretKey} are set, a {@code StaticCredentialsProvider} is used
     * instead of the default provider chain.  Intended for local development
     * against MinIO.
     */
    private String accessKey;

    /**
     * Optional static secret key.  See {@link #accessKey}.
     */
    private String secretKey;
}