package com.adb4.rmlmanager.dto.response;

import com.adb4.rmlmanager.enums.AssetStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record AssetSummaryResponse(
        UUID id,
        String code,
        String title,
        AssetStatus status,
        String preview,
        String categoryName,
        String subcategoryName,
        boolean hasAnimation,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
