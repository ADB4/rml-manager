package com.adb4.rmlmanager.dto.request;

import com.adb4.rmlmanager.enums.AssetStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateAssetRequest(
        @NotBlank String title,
        @NotNull UUID subcategoryId,
        String description,
        boolean hasAnimation,
        AssetStatus status
) {}
