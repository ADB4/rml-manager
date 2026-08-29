package com.adb4.rmlmanager.mapper;

import com.adb4.rmlmanager.dto.response.TextureSetResponse;
import com.adb4.rmlmanager.entity.TextureSet;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps {@link TextureSet} to its API response. MapStruct emits null-safe
 * navigation for {@code lod.id} and {@code meshPart.id}, so unscoped sets map
 * to null identifiers. Map ordering comes from the {@code @OrderBy} on the
 * entity collection.
 */
@Mapper(componentModel = "spring", uses = TextureMapMapper.class)
public interface TextureSetMapper {

    @Mapping(source = "variant.id", target = "variantId")
    @Mapping(source = "lod.id", target = "lodId")
    @Mapping(source = "meshPart.id", target = "meshPartId")
    @Mapping(source = "textureMaps", target = "maps")
    TextureSetResponse toResponse(TextureSet textureSet);
}
