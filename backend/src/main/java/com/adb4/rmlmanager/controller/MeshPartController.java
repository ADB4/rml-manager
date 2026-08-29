package com.adb4.rmlmanager.controller;

import com.adb4.rmlmanager.dto.request.MeshPartRequest;
import com.adb4.rmlmanager.dto.response.MeshPartResponse;
import com.adb4.rmlmanager.service.MeshPartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assets/{assetId}/mesh-parts")
public class MeshPartController {

    private final MeshPartService meshPartService;

    public MeshPartController(MeshPartService meshPartService) {
        this.meshPartService = meshPartService;
    }

    @GetMapping
    public List<MeshPartResponse> findByAssetId(@PathVariable UUID assetId) {
        return meshPartService.findByAssetId(assetId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeshPartResponse create(@PathVariable UUID assetId,
                                   @Valid @RequestBody MeshPartRequest request) {
        return meshPartService.create(assetId, request);
    }

    @PutMapping("/{meshPartId}")
    public MeshPartResponse update(@PathVariable UUID assetId,
                                   @PathVariable UUID meshPartId,
                                   @Valid @RequestBody MeshPartRequest request) {
        return meshPartService.update(assetId, meshPartId, request);
    }

    @DeleteMapping("/{meshPartId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID assetId, @PathVariable UUID meshPartId) {
        meshPartService.delete(assetId, meshPartId);
    }
}
