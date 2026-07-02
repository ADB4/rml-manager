package com.adb4.rmlmanager.mapper;

import com.adb4.rmlmanager.dto.request.CreateAssetRequest;
import com.adb4.rmlmanager.dto.request.UpdateAssetRequest;
import com.adb4.rmlmanager.dto.response.AssetSummaryResponse;
import com.adb4.rmlmanager.entity.Asset;
import com.adb4.rmlmanager.entity.Subcategory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AssetMapper {

    @Mapping(source = "subcategory.category.name", target = "categoryName")
    @Mapping(source = "subcategory.name", target = "subcategoryName")
    AssetSummaryResponse toSummaryResponse(Asset asset);

    @Mapping(source = "request.code", target = "code")
    @Mapping(source = "request.title", target = "title")
    @Mapping(source = "request.description", target = "description")
    @Mapping(source = "request.hasAnimation", target = "hasAnimation")
    @Mapping(source = "subcategory", target = "subcategory")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "preview", ignore = true)
    @Mapping(target = "version", constant = "1")
    @Mapping(target = "status", constant = "DRAFT")
    Asset toEntity(CreateAssetRequest request, Subcategory subcategory);

    @Mapping(source = "request.title", target = "title")
    @Mapping(source = "request.description", target = "description")
    @Mapping(source = "request.hasAnimation", target = "hasAnimation")
    @Mapping(source = "request.status", target = "status")
    @Mapping(source = "subcategory", target = "subcategory")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "preview", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(UpdateAssetRequest request, Subcategory subcategory, @MappingTarget Asset asset);
}