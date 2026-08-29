package com.adb4.rmlmanager.dto.response;

import com.adb4.rmlmanager.enums.TextureFileType;
import com.adb4.rmlmanager.enums.TextureMapType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * API view of a stored texture map. Internal storage coordinates
 * ({@code s3Key}, {@code s3Bucket}) are deliberately omitted, matching
 * {@link GeometryResponse}. {@code width} and {@code height} are null when the
 * image format has no dimension reader available (for example SVG).
 */
public record TextureMapResponse(
        UUID id,
        TextureMapType type,
        String fileName,
        TextureFileType fileType,
        Integer fileSize,
        Integer width,
        Integer height,
        String checksum,
        UUID uploadedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
