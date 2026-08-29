package com.adb4.rmlmanager.controller;

import com.adb4.rmlmanager.dto.request.LodRequest;
import com.adb4.rmlmanager.dto.response.LodResponse;
import com.adb4.rmlmanager.service.LodService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assets/{assetId}/lods")
public class LodController {

    private final LodService lodService;

    public LodController(LodService lodService) {
        this.lodService = lodService;
    }

    @GetMapping
    public List<LodResponse> findByAssetId(@PathVariable UUID assetId) {
        return lodService.findByAssetId(assetId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LodResponse create(@PathVariable UUID assetId,
                              @Valid @RequestBody LodRequest request) {
        return lodService.create(assetId, request);
    }

    @DeleteMapping("/{lodId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID assetId, @PathVariable UUID lodId) {
        lodService.delete(assetId, lodId);
    }
}
