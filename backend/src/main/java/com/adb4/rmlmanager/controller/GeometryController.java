package com.adb4.rmlmanager.controller;

import com.adb4.rmlmanager.dto.response.GeometryResponse;
import com.adb4.rmlmanager.enums.GeometryFileType;
import com.adb4.rmlmanager.service.GeometryService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Geometry upload endpoint, nested under a LOD.
 *
 * <p>Multipart form: a {@code file} part plus {@code fileType} and optional
 * {@code meshPartId} form fields. Authentication is required (enforced globally
 * by SecurityConfig) so that {@code @CreatedBy} can resolve {@code uploadedBy}.
 * Per-asset authorization ({@code canEdit}) is layered on in KAN-27.
 */
@RestController
@RequestMapping("/api/lods/{lodId}/geometries")
public class GeometryController {

    private final GeometryService geometryService;

    public GeometryController(GeometryService geometryService) {
        this.geometryService = geometryService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GeometryResponse> upload(
            @PathVariable UUID lodId,
            @RequestParam("fileType") GeometryFileType fileType,
            @RequestParam(value = "meshPartId", required = false) UUID meshPartId,
            @RequestPart("file") MultipartFile file,
            UriComponentsBuilder ucb) {

        GeometryResponse response = geometryService.upload(lodId, fileType, meshPartId, file);

        // Canonical resource URI. The matching GET (presigned download) arrives
        // with KAN-23; the Location contract is stable regardless.
        URI location = ucb.path("/api/geometries/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }
}
