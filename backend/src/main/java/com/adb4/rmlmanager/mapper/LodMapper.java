package com.adb4.rmlmanager.mapper;

import com.adb4.rmlmanager.dto.request.LodRequest;
import com.adb4.rmlmanager.dto.response.LodResponse;
import com.adb4.rmlmanager.entity.Asset;
import com.adb4.rmlmanager.entity.Lod;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface LodMapper {

    @Mapping(source = "asset.id", target = "assetId")
    LodResponse toResponse(Lod lod);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(source = "request.level", target = "level")
    @Mapping(source = "asset", target = "asset")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Lod toEntity(LodRequest request, Asset asset);
}
