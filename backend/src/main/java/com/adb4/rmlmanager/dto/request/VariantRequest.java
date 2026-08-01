package com.adb4.rmlmanager.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VariantRequest(
        @NotBlank @Size(max = 32) String code,
        @NotBlank @Size(max = 255) String displayName,
        @Size(max = 1024) String description,

        /*
         * Column is varchar(6) and stores RGB without the leading '#'.
         * @Pattern treats null as valid, which gives us the "when present"
         * semantics the ticket asks for.
         */
        @Pattern(
                regexp = "^[0-9a-fA-F]{6}$",
                message = "must be exactly 6 hexadecimal characters with no leading '#'"
        )
        String colorHex,

        Integer sortOrder,
        Boolean isDefault
) {}