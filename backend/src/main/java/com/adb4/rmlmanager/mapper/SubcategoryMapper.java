package com.adb4.rmlmanager.mapper;

import com.adb4.rmlmanager.dto.request.SubcategoryRequest;
import com.adb4.rmlmanager.dto.response.SubcategoryResponse;
import com.adb4.rmlmanager.entity.Category;
import com.adb4.rmlmanager.entity.Subcategory;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface SubcategoryMapper {

    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "category.name", target = "categoryName")
    SubcategoryResponse toResponse(Subcategory subcategory);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(source = "request.name", target = "name")
    @Mapping(source = "category", target = "category")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Subcategory toEntity(SubcategoryRequest request, Category category);

    @Mapping(source = "request.name", target = "name")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(SubcategoryRequest request, @MappingTarget Subcategory subcategory);
}