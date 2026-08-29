package com.adb4.rmlmanager.mapper;

import com.adb4.rmlmanager.dto.response.TextureMapResponse;
import com.adb4.rmlmanager.entity.TextureMap;
import org.mapstruct.Mapper;

/**
 * Maps {@link TextureMap} to its API response.
 *
 * <p>Only {@code toResponse} is provided: like {@code GeometryMapper}, the
 * entity is assembled in the service because building it is entangled with the
 * S3 upload side effect (key, checksum, dimensions).
 */
@Mapper(componentModel = "spring")
public interface TextureMapMapper {

    TextureMapResponse toResponse(TextureMap textureMap);
}
