package com.adb4.rmlmanager.controller;

import com.adb4.rmlmanager.dto.request.VariantRequest;
import com.adb4.rmlmanager.dto.response.VariantResponse;
import com.adb4.rmlmanager.service.VariantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assets/{assetId}/variants")
public class VariantController {

    private final VariantService variantService;

    public VariantController(VariantService variantService) {
        this.variantService = variantService;
    }

    @GetMapping
    public List<VariantResponse> findByAssetId(@PathVariable UUID assetId) {
        return variantService.findByAssetId(assetId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VariantResponse create(@PathVariable UUID assetId,
                                  @Valid @RequestBody VariantRequest request) {
        return variantService.create(assetId, request);
    }

    @PutMapping("/{variantId}")
    public VariantResponse update(@PathVariable UUID assetId,
                                  @PathVariable UUID variantId,
                                  @Valid @RequestBody VariantRequest request) {
        return variantService.update(assetId, variantId, request);
    }

    @DeleteMapping("/{variantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID assetId, @PathVariable UUID variantId) {
        variantService.delete(assetId, variantId);
    }
}