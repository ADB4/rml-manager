package com.adb4.rmlmanager.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record LodResponse(
        UUID id,
        UUID assetId,
        Integer level,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
