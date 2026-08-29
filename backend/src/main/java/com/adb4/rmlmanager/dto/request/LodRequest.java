package com.adb4.rmlmanager.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record LodRequest(
        /*
         * Levels are zero-based: 0 is the full-detail mesh and higher values
         * are progressively coarser. The column has no check constraint, so
         * negatives are rejected here.
         */
        @NotNull @PositiveOrZero Integer level
) {}
