package com.adb4.rmlmanager.controller;

import com.adb4.rmlmanager.dto.response.TextureMapResponse;
import com.adb4.rmlmanager.enums.TextureMapType;
import com.adb4.rmlmanager.service.TextureMapService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Texture map endpoints, nested under a texture set.
 *
 * <p>Upload is a multipart form: a {@code file} part plus a {@code type}
 * parameter naming the {@link TextureMapType}. The file format is derived from
 * the extension and must be a supported
 * {@link com.adb4.rmlmanager.enums.TextureFileType}. Identical content is
 * deduplicated by checksum: re-uploading bytes that are already stored links
 * the existing map into this set instead of storing a second copy.
 *
 * <p>Authentication is required (enforced globally by SecurityConfig) so that
 * {@code @CreatedBy} can resolve {@code uploadedBy}. Per-asset authorization
 * ({@code canEdit}) is layered on in KAN-27.
 */
@RestController
@RequestMapping("/api/texture-sets/{textureSetId}/maps")
public class TextureMapController {

    private final TextureMapService textureMapService;

    public TextureMapController(TextureMapService textureMapService) {
        this.textureMapService = textureMapService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TextureMapResponse> upload(
            @PathVariable UUID textureSetId,
            @RequestParam("type") TextureMapType type,
            @RequestPart("file") MultipartFile file,
            UriComponentsBuilder ucb) {

        TextureMapResponse response = textureMapService.upload(textureSetId, type, file);

        // Canonical resource URI. The matching GET (presigned download) is
        // future work; the Location contract is stable regardless.
        URI location = ucb.path("/api/texture-maps/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Removes a map from this set. Association only: the map row and its S3
     * object remain, because either may be shared with other sets through
     * checksum deduplication.
     */
    @DeleteMapping("/{textureMapId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID textureSetId, @PathVariable UUID textureMapId) {
        textureMapService.removeFromSet(textureSetId, textureMapId);
    }
}
