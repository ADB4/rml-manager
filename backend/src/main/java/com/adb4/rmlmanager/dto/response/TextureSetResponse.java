package com.adb4.rmlmanager.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * API view of a texture set. {@code lodId} and {@code meshPartId} are null for
 * the unscoped (shared across LODs, whole-asset) case. The entity's
 * {@code s3Key}/{@code s3Bucket}/{@code checksum} columns describe an optional
 * zip archive this API does not produce, so they are omitted here.
 */
public record TextureSetResponse(
        UUID id,
        UUID variantId,
        UUID lodId,
        UUID meshPartId,
        Integer version,
        Boolean isLatest,
        List<TextureMapResponse> maps,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
