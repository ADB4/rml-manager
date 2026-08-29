package com.adb4.rmlmanager.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record MeshPartResponse(
        UUID id,
        UUID assetId,
        String code,
        String shader,
        String material,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
