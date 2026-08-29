package com.adb4.rmlmanager.dto.response;

import com.adb4.rmlmanager.enums.GeometryFileType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * API view of a stored geometry file. Internal storage coordinates
 * ({@code s3Key}, {@code s3Bucket}) are deliberately omitted; the download URL
 * is issued separately by the presigned-download endpoint (KAN-23).
 * {@code meshPartId} is {@code null} for whole-asset (baked) geometry.
 */
public record GeometryResponse(
        UUID id,
        UUID lodId,
        UUID meshPartId,
        String fileName,
        GeometryFileType fileType,
        Long fileSize,
        String contentType,
        Integer version,
        Boolean isLatest,
        String checksum,
        UUID uploadedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
