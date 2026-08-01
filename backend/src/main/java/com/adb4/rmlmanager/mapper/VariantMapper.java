package com.adb4.rmlmanager.mapper;

import com.adb4.rmlmanager.dto.request.VariantRequest;
import com.adb4.rmlmanager.dto.response.VariantResponse;
import com.adb4.rmlmanager.entity.Asset;
import com.adb4.rmlmanager.entity.Variant;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface VariantMapper {

    @Mapping(source = "asset.id", target = "assetId")
    VariantResponse toResponse(Variant variant);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(source = "request.code", target = "code")
    @Mapping(source = "request.displayName", target = "displayName")
    @Mapping(source = "request.description", target = "description")
    @Mapping(source = "request.colorHex", target = "colorHex")
    @Mapping(source = "request.sortOrder", target = "sortOrder")
    @Mapping(source = "request.isDefault", target = "isDefault")
    @Mapping(source = "asset", target = "asset")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Variant toEntity(VariantRequest request, Asset asset);

    @Mapping(source = "request.code", target = "code")
    @Mapping(source = "request.displayName", target = "displayName")
    @Mapping(source = "request.description", target = "description")
    @Mapping(source = "request.colorHex", target = "colorHex")
    @Mapping(source = "request.sortOrder", target = "sortOrder")
    @Mapping(source = "request.isDefault", target = "isDefault")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "asset", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(VariantRequest request, @MappingTarget Variant variant);
}