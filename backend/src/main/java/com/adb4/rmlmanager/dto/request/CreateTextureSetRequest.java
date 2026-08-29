package com.adb4.rmlmanager.dto.request;

import java.util.UUID;

/**
 * Payload for creating a texture set under a variant. Both scope fields are
 * optional: a null {@code lodId} means the set is shared across all LOD levels
 * (the standard case) and a null {@code meshPartId} means it covers the
 * whole-asset merged geometry.
 */
public record CreateTextureSetRequest(
        UUID lodId,
        UUID meshPartId
) {}
