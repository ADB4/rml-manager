package com.adb4.rmlmanager.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record SubcategoryResponse(
        UUID id,
        String name,
        UUID categoryId,
        String categoryName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}