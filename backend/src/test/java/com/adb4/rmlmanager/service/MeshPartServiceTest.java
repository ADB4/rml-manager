package com.adb4.rmlmanager.service;

import com.adb4.rmlmanager.dto.request.MeshPartRequest;
import com.adb4.rmlmanager.dto.response.MeshPartResponse;
import com.adb4.rmlmanager.entity.Asset;
import com.adb4.rmlmanager.entity.MeshPart;
import com.adb4.rmlmanager.exception.DuplicateResourceException;
import com.adb4.rmlmanager.exception.ResourceInUseException;
import com.adb4.rmlmanager.exception.ResourceNotFoundException;
import com.adb4.rmlmanager.mapper.MeshPartMapper;
import com.adb4.rmlmanager.repository.AssetRepository;
import com.adb4.rmlmanager.repository.GeometryRepository;
import com.adb4.rmlmanager.repository.MeshPartRepository;
import com.adb4.rmlmanager.repository.TextureSetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeshPartServiceTest {

    @Mock
    private MeshPartRepository meshPartRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private GeometryRepository geometryRepository;

    @Mock
    private TextureSetRepository textureSetRepository;

    @Mock
    private MeshPartMapper meshPartMapper;

    @InjectMocks
    private MeshPartService meshPartService;

    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID OTHER_ASSET_ID = UUID.randomUUID();
    private static final UUID MESH_PART_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.now();

    private Asset asset() {
        return Asset.builder().id(ASSET_ID).build();
    }

    private MeshPart meshPart(String code) {
        return MeshPart.builder().id(MESH_PART_ID).asset(asset()).code(code).build();
    }

    private MeshPartResponse response(String code) {
        return new MeshPartResponse(MESH_PART_ID, ASSET_ID, code, "standard", "oak", NOW, NOW);
    }

    // ---- findByAssetId ----

    @Test
    void findByAssetId_returnsList() {
        MeshPart part = meshPart("seat");
        when(assetRepository.existsById(ASSET_ID)).thenReturn(true);
        when(meshPartRepository.findByAssetIdOrderByCodeAsc(ASSET_ID)).thenReturn(List.of(part));
        when(meshPartMapper.toResponse(part)).thenReturn(response("seat"));

        List<MeshPartResponse> result = meshPartService.findByAssetId(ASSET_ID);

        assertEquals(1, result.size());
        assertEquals("seat", result.get(0).code());
    }

    @Test
    void findByAssetId_whenAssetNotFound_throwsResourceNotFoundException() {
        when(assetRepository.existsById(ASSET_ID)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> meshPartService.findByAssetId(ASSET_ID));
    }

    // ---- create ----

    @Test
    void create_savesAndReturnsResponse() {
        Asset asset = asset();
        MeshPartRequest request = new MeshPartRequest("seat", "standard", "oak");
        MeshPart entity = meshPart("seat");

        when(assetRepository.findById(ASSET_ID)).thenReturn(Optional.of(asset));
        when(meshPartRepository.existsByAssetIdAndCode(ASSET_ID, "seat")).thenReturn(false);
        when(meshPartMapper.toEntity(request, asset)).thenReturn(entity);
        when(meshPartRepository.save(entity)).thenReturn(entity);
        when(meshPartMapper.toResponse(entity)).thenReturn(response("seat"));

        MeshPartResponse result = meshPartService.create(ASSET_ID, request);

        assertEquals("seat", result.code());
        verify(meshPartRepository).save(entity);
    }

    @Test
    void create_whenAssetNotFound_throwsResourceNotFoundException() {
        when(assetRepository.findById(ASSET_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> meshPartService.create(ASSET_ID, new MeshPartRequest("seat", null, null)));
        verify(meshPartRepository, never()).save(any());
    }

    @Test
    void create_whenDuplicateCode_throwsDuplicateResourceException() {
        when(assetRepository.findById(ASSET_ID)).thenReturn(Optional.of(asset()));
        when(meshPartRepository.existsByAssetIdAndCode(ASSET_ID, "seat")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> meshPartService.create(ASSET_ID, new MeshPartRequest("seat", null, null)));
        verify(meshPartRepository, never()).save(any());
    }

    // ---- update ----

    @Test
    void update_whenCodeChanged_updatesAndReturnsResponse() {
        MeshPart existing = meshPart("old");
        MeshPartRequest request = new MeshPartRequest("new", "standard", "oak");

        when(meshPartRepository.findById(MESH_PART_ID)).thenReturn(Optional.of(existing));
        when(meshPartRepository.existsByAssetIdAndCode(ASSET_ID, "new")).thenReturn(false);
        when(meshPartRepository.save(existing)).thenReturn(existing);
        when(meshPartMapper.toResponse(existing)).thenReturn(response("new"));

        MeshPartResponse result = meshPartService.update(ASSET_ID, MESH_PART_ID, request);

        assertEquals("new", result.code());
        verify(meshPartMapper).updateEntity(request, existing);
    }

    @Test
    void update_whenCodeUnchanged_skipsDuplicateCheck() {
        MeshPart existing = meshPart("same");
        MeshPartRequest request = new MeshPartRequest("same", "standard", "oak");

        when(meshPartRepository.findById(MESH_PART_ID)).thenReturn(Optional.of(existing));
        when(meshPartRepository.save(existing)).thenReturn(existing);
        when(meshPartMapper.toResponse(existing)).thenReturn(response("same"));

        meshPartService.update(ASSET_ID, MESH_PART_ID, request);

        verify(meshPartRepository, never()).existsByAssetIdAndCode(any(), anyString());
    }

    @Test
    void update_whenCodeChangedToDuplicate_throwsDuplicateResourceException() {
        MeshPart existing = meshPart("original");

        when(meshPartRepository.findById(MESH_PART_ID)).thenReturn(Optional.of(existing));
        when(meshPartRepository.existsByAssetIdAndCode(ASSET_ID, "taken")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> meshPartService.update(ASSET_ID, MESH_PART_ID,
                        new MeshPartRequest("taken", null, null)));
        verify(meshPartRepository, never()).save(any());
    }

    @Test
    void update_whenMeshPartNotFound_throwsResourceNotFoundException() {
        when(meshPartRepository.findById(MESH_PART_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> meshPartService.update(ASSET_ID, MESH_PART_ID,
                        new MeshPartRequest("any", null, null)));
    }

    @Test
    void update_whenMeshPartBelongsToDifferentAsset_throwsResourceNotFoundException() {
        MeshPart wrongParent = MeshPart.builder()
                .id(MESH_PART_ID)
                .asset(Asset.builder().id(OTHER_ASSET_ID).build())
                .code("seat")
                .build();

        when(meshPartRepository.findById(MESH_PART_ID)).thenReturn(Optional.of(wrongParent));

        assertThrows(ResourceNotFoundException.class,
                () -> meshPartService.update(ASSET_ID, MESH_PART_ID,
                        new MeshPartRequest("seat", null, null)));
    }

    // ---- delete ----

    @Test
    void delete_removesMeshPart() {
        MeshPart existing = meshPart("seat");

        when(meshPartRepository.findById(MESH_PART_ID)).thenReturn(Optional.of(existing));
        when(geometryRepository.existsByMeshPartId(MESH_PART_ID)).thenReturn(false);
        when(textureSetRepository.existsByMeshPartId(MESH_PART_ID)).thenReturn(false);

        meshPartService.delete(ASSET_ID, MESH_PART_ID);

        verify(meshPartRepository).delete(existing);
    }

    @Test
    void delete_whenMeshPartNotFound_throwsResourceNotFoundException() {
        when(meshPartRepository.findById(MESH_PART_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> meshPartService.delete(ASSET_ID, MESH_PART_ID));
        verify(meshPartRepository, never()).delete(any());
    }

    @Test
    void delete_whenMeshPartBelongsToDifferentAsset_throwsResourceNotFoundException() {
        MeshPart wrongParent = MeshPart.builder()
                .id(MESH_PART_ID)
                .asset(Asset.builder().id(OTHER_ASSET_ID).build())
                .code("seat")
                .build();

        when(meshPartRepository.findById(MESH_PART_ID)).thenReturn(Optional.of(wrongParent));

        assertThrows(ResourceNotFoundException.class,
                () -> meshPartService.delete(ASSET_ID, MESH_PART_ID));
        verify(meshPartRepository, never()).delete(any());
    }

    @Test
    void delete_whenGeometriesReferenceMeshPart_throwsResourceInUseException() {
        when(meshPartRepository.findById(MESH_PART_ID)).thenReturn(Optional.of(meshPart("seat")));
        when(geometryRepository.existsByMeshPartId(MESH_PART_ID)).thenReturn(true);

        assertThrows(ResourceInUseException.class,
                () -> meshPartService.delete(ASSET_ID, MESH_PART_ID));
        verify(meshPartRepository, never()).delete(any());
    }

    @Test
    void delete_whenTextureSetsReferenceMeshPart_throwsResourceInUseException() {
        when(meshPartRepository.findById(MESH_PART_ID)).thenReturn(Optional.of(meshPart("seat")));
        when(geometryRepository.existsByMeshPartId(MESH_PART_ID)).thenReturn(false);
        when(textureSetRepository.existsByMeshPartId(MESH_PART_ID)).thenReturn(true);

        assertThrows(ResourceInUseException.class,
                () -> meshPartService.delete(ASSET_ID, MESH_PART_ID));
        verify(meshPartRepository, never()).delete(any());
    }
}
