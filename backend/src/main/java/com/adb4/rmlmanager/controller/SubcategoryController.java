package com.adb4.rmlmanager.controller;

import com.adb4.rmlmanager.dto.request.SubcategoryRequest;
import com.adb4.rmlmanager.dto.response.SubcategoryResponse;
import com.adb4.rmlmanager.service.SubcategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories/{categoryId}/subcategories")
public class SubcategoryController {

    private final SubcategoryService subcategoryService;

    public SubcategoryController(SubcategoryService subcategoryService) {
        this.subcategoryService = subcategoryService;
    }

    @GetMapping
    public List<SubcategoryResponse> findByCategoryId(@PathVariable UUID categoryId) {
        return subcategoryService.findByCategoryId(categoryId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public SubcategoryResponse create(@PathVariable UUID categoryId,
                                      @Valid @RequestBody SubcategoryRequest request) {
        return  subcategoryService.create(categoryId, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SubcategoryResponse update(@PathVariable UUID categoryId,
                                      @PathVariable UUID id,
                                      @Valid @RequestBody SubcategoryRequest request) {
        return  subcategoryService.update(categoryId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID categoryId, @PathVariable UUID id) {
        subcategoryService.delete(categoryId, id);
    }
}