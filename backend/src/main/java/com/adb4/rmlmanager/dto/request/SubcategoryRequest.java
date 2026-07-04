package com.adb4.rmlmanager.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubcategoryRequest(
        @NotBlank @Size(max = 64) String name
) {}