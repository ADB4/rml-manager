package com.adb4.rmlmanager.controller;

import com.adb4.rmlmanager.dto.request.CreateAssetRequest;
import com.adb4.rmlmanager.dto.request.UpdateAssetRequest;
import com.adb4.rmlmanager.dto.response.AssetSummaryResponse;
import com.adb4.rmlmanager.security.AppUserPrincipal;
import com.adb4.rmlmanager.service.AssetService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    public Page<AssetSummaryResponse> findAll(@AuthenticationPrincipal AppUserPrincipal principal,
                                              Pageable pageable) {
        return assetService.findAllVisible(principal.getId(), pageable);
    }

    @GetMapping("/{code}")
    public AssetSummaryResponse findByCode(@PathVariable String code) {
        return assetService.findByCode(code);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssetSummaryResponse create(@Valid @RequestBody CreateAssetRequest request) {
        return assetService.create(request);
    }

    @PutMapping("/{id}")
    public AssetSummaryResponse update(@PathVariable UUID id,
                                       @Valid @RequestBody UpdateAssetRequest request) {
        return assetService.update(id, request);
    }
}