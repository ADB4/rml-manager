package com.adb4.rmlmanager.controller;

import com.adb4.rmlmanager.dto.request.CreateTextureSetRequest;
import com.adb4.rmlmanager.dto.response.TextureSetResponse;
import com.adb4.rmlmanager.service.TextureSetService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Texture set creation endpoint, nested under a variant.
 *
 * <p>A set groups the texture maps for a variant, optionally narrowed to a
 * specific LOD and/or mesh part (send {@code {}} for the unscoped standard
 * case). Creation follows the same version/isLatest semantics as geometry
 * uploads: the new set becomes the latest version within its
 * (variant, lod, meshPart) scope and the previous latest is flipped.
 *
 * <p>Authentication is required (enforced globally by SecurityConfig).
 * Per-asset authorization ({@code canEdit}) is layered on in KAN-27.
 */
@RestController
@RequestMapping("/api/variants/{variantId}/texture-sets")
public class TextureSetController {

    private final TextureSetService textureSetService;

    public TextureSetController(TextureSetService textureSetService) {
        this.textureSetService = textureSetService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TextureSetResponse create(@PathVariable UUID variantId,
                                     @RequestBody CreateTextureSetRequest request) {
        return textureSetService.create(variantId, request);
    }
}
