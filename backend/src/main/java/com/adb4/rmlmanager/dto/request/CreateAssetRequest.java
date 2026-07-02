package com.adb4.rmlmanager.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateAssetRequest(
    @NotBlank @Size(max = 32) String code,
    @NotBlank String title,
    @NotNull UUID subcategoryId,
    String description,
    boolean hasAnimation
) {
}
