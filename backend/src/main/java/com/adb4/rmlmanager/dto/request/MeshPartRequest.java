package com.adb4.rmlmanager.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MeshPartRequest(
        @NotBlank @Size(max = 64) String code,
        @Size(max = 255) String shader,
        @Size(max = 255) String material
) {}
