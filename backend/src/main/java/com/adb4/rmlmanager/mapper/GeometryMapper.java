package com.adb4.rmlmanager.mapper;

import com.adb4.rmlmanager.dto.response.GeometryResponse;
import com.adb4.rmlmanager.entity.Geometry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps {@link Geometry} to its API response.
 *
 * <p>Only {@code toResponse} is provided: the entity is assembled in
 * {@code GeometryService} rather than mapped from a request, because building it
 * is entangled with the S3 upload side effect (key, checksum, size, version).
 * MapStruct emits null-safe navigation for {@code meshPart.id}, so baked
 * geometry (null mesh part) maps to a null {@code meshPartId}.
 */
@Mapper(componentModel = "spring")
public interface GeometryMapper {

    @Mapping(source = "lod.id", target = "lodId")
    @Mapping(source = "meshPart.id", target = "meshPartId")
    GeometryResponse toResponse(Geometry geometry);
}
