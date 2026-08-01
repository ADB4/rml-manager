package com.adb4.rmlmanager.controller;

import com.adb4.rmlmanager.dto.request.CreateAssetRequest;
import com.adb4.rmlmanager.dto.request.UpdateAssetRequest;
import com.adb4.rmlmanager.dto.response.AssetSummaryResponse;
import com.adb4.rmlmanager.enums.AssetStatus;
import com.adb4.rmlmanager.security.AppUserPrincipal;
import com.adb4.rmlmanager.service.AssetService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    public Page<AssetSummaryResponse> findAll(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID subcategoryId,
            @RequestParam(required = false) AssetStatus status,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        return assetService.findAllVisible(
                principal.getId(), categoryId, subcategoryId, status, q, pageable);
    }

    @GetMapping("/{code}")
    public AssetSummaryResponse findByCode(@PathVariable String code) {
        return assetService.findByCode(code);
    }

    @PostMapping
    public ResponseEntity<AssetSummaryResponse> create(
            @Valid @RequestBody CreateAssetRequest request,
            UriComponentsBuilder ucb) {
        AssetSummaryResponse response = assetService.create(request);
        URI location = ucb.path("/api/assets/{code}")
                .buildAndExpand(response.code())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public AssetSummaryResponse update(@PathVariable UUID id,
                                       @Valid @RequestBody UpdateAssetRequest request) {
        return assetService.update(id, request);
    }
}