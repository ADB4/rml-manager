package com.adb4.rmlmanager.mapper;

import com.adb4.rmlmanager.dto.request.MeshPartRequest;
import com.adb4.rmlmanager.dto.response.MeshPartResponse;
import com.adb4.rmlmanager.entity.Asset;
import com.adb4.rmlmanager.entity.MeshPart;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface MeshPartMapper {

    @Mapping(source = "asset.id", target = "assetId")
    MeshPartResponse toResponse(MeshPart meshPart);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(source = "request.code", target = "code")
    @Mapping(source = "request.shader", target = "shader")
    @Mapping(source = "request.material", target = "material")
    @Mapping(source = "asset", target = "asset")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    MeshPart toEntity(MeshPartRequest request, Asset asset);

    @Mapping(source = "request.code", target = "code")
    @Mapping(source = "request.shader", target = "shader")
    @Mapping(source = "request.material", target = "material")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "asset", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(MeshPartRequest request, @MappingTarget MeshPart meshPart);
}
