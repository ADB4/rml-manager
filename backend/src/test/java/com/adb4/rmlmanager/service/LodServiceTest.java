package com.adb4.rmlmanager.service;

import com.adb4.rmlmanager.dto.request.LodRequest;
import com.adb4.rmlmanager.dto.response.LodResponse;
import com.adb4.rmlmanager.entity.Asset;
import com.adb4.rmlmanager.entity.Lod;
import com.adb4.rmlmanager.exception.DuplicateResourceException;
import com.adb4.rmlmanager.exception.ResourceInUseException;
import com.adb4.rmlmanager.exception.ResourceNotFoundException;
import com.adb4.rmlmanager.mapper.LodMapper;
import com.adb4.rmlmanager.repository.AssetRepository;
import com.adb4.rmlmanager.repository.GeometryRepository;
import com.adb4.rmlmanager.repository.LodRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LodServiceTest {

    @Mock
    private LodRepository lodRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private GeometryRepository geometryRepository;

    @Mock
    private TextureSetRepository textureSetRepository;

    @Mock
    private LodMapper lodMapper;

    @InjectMocks
    private LodService lodService;

    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID OTHER_ASSET_ID = UUID.randomUUID();
    private static final UUID LOD_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.now();

    private Asset asset() {
        return Asset.builder().id(ASSET_ID).build();
    }

    private Lod lod(int level) {
        return Lod.builder().id(LOD_ID).asset(asset()).level(level).build();
    }

    private LodResponse response(int level) {
        return new LodResponse(LOD_ID, ASSET_ID, level, NOW, NOW);
    }

    // ---- findByAssetId ----

    @Test
    void findByAssetId_returnsListOrderedByLevel() {
        Lod lod = lod(0);
        when(assetRepository.existsById(ASSET_ID)).thenReturn(true);
        when(lodRepository.findByAssetIdOrderByLevelAsc(ASSET_ID)).thenReturn(List.of(lod));
        when(lodMapper.toResponse(lod)).thenReturn(response(0));

        List<LodResponse> result = lodService.findByAssetId(ASSET_ID);

        assertEquals(1, result.size());
        assertEquals(0, result.get(0).level());
    }

    @Test
    void findByAssetId_whenAssetNotFound_throwsResourceNotFoundException() {
        when(assetRepository.existsById(ASSET_ID)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
                () -> lodService.findByAssetId(ASSET_ID));
    }

    // ---- create ----

    @Test
    void create_savesAndReturnsResponse() {
        Asset asset = asset();
        LodRequest request = new LodRequest(1);
        Lod entity = lod(1);

        when(assetRepository.findById(ASSET_ID)).thenReturn(Optional.of(asset));
        when(lodRepository.existsByAssetIdAndLevel(ASSET_ID, 1)).thenReturn(false);
        when(lodMapper.toEntity(request, asset)).thenReturn(entity);
        when(lodRepository.save(entity)).thenReturn(entity);
        when(lodMapper.toResponse(entity)).thenReturn(response(1));

        LodResponse result = lodService.create(ASSET_ID, request);

        assertEquals(1, result.level());
        verify(lodRepository).save(entity);
    }

    @Test
    void create_whenAssetNotFound_throwsResourceNotFoundException() {
        when(assetRepository.findById(ASSET_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> lodService.create(ASSET_ID, new LodRequest(0)));
        verify(lodRepository, never()).save(any());
    }

    @Test
    void create_whenDuplicateLevel_throwsDuplicateResourceException() {
        when(assetRepository.findById(ASSET_ID)).thenReturn(Optional.of(asset()));
        when(lodRepository.existsByAssetIdAndLevel(ASSET_ID, 0)).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> lodService.create(ASSET_ID, new LodRequest(0)));
        verify(lodRepository, never()).save(any());
    }

    // ---- delete ----

    @Test
    void delete_removesLod() {
        Lod existing = lod(0);

        when(lodRepository.findById(LOD_ID)).thenReturn(Optional.of(existing));
        when(geometryRepository.existsByLodId(LOD_ID)).thenReturn(false);
        when(textureSetRepository.existsByLodId(LOD_ID)).thenReturn(false);

        lodService.delete(ASSET_ID, LOD_ID);

        verify(lodRepository).delete(existing);
    }

    @Test
    void delete_whenLodNotFound_throwsResourceNotFoundException() {
        when(lodRepository.findById(LOD_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> lodService.delete(ASSET_ID, LOD_ID));
        verify(lodRepository, never()).delete(any());
    }

    @Test
    void delete_whenLodBelongsToDifferentAsset_throwsResourceNotFoundException() {
        Lod wrongParent = Lod.builder()
                .id(LOD_ID)
                .asset(Asset.builder().id(OTHER_ASSET_ID).build())
                .level(0)
                .build();

        when(lodRepository.findById(LOD_ID)).thenReturn(Optional.of(wrongParent));

        assertThrows(ResourceNotFoundException.class,
                () -> lodService.delete(ASSET_ID, LOD_ID));
        verify(lodRepository, never()).delete(any());
    }

    @Test
    void delete_whenGeometriesReferenceLod_throwsResourceInUseException() {
        when(lodRepository.findById(LOD_ID)).thenReturn(Optional.of(lod(0)));
        when(geometryRepository.existsByLodId(LOD_ID)).thenReturn(true);

        assertThrows(ResourceInUseException.class,
                () -> lodService.delete(ASSET_ID, LOD_ID));
        verify(lodRepository, never()).delete(any());
    }

    @Test
    void delete_whenTextureSetsReferenceLod_throwsResourceInUseException() {
        when(lodRepository.findById(LOD_ID)).thenReturn(Optional.of(lod(0)));
        when(geometryRepository.existsByLodId(LOD_ID)).thenReturn(false);
        when(textureSetRepository.existsByLodId(LOD_ID)).thenReturn(true);

        assertThrows(ResourceInUseException.class,
                () -> lodService.delete(ASSET_ID, LOD_ID));
        verify(lodRepository, never()).delete(any());
    }
}
