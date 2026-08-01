package com.adb4.rmlmanager.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record VariantResponse(
        UUID id,
        UUID assetId,
        String code,
        String displayName,
        String description,
        String colorHex,
        Integer sortOrder,
        Boolean isDefault,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}