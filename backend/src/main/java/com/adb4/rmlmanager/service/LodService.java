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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class LodService {

    private final LodRepository lodRepository;
    private final AssetRepository assetRepository;
    private final GeometryRepository geometryRepository;
    private final TextureSetRepository textureSetRepository;
    private final LodMapper lodMapper;

    public LodService(LodRepository lodRepository,
                      AssetRepository assetRepository,
                      GeometryRepository geometryRepository,
                      TextureSetRepository textureSetRepository,
                      LodMapper lodMapper) {
        this.lodRepository = lodRepository;
        this.assetRepository = assetRepository;
        this.geometryRepository = geometryRepository;
        this.textureSetRepository = textureSetRepository;
        this.lodMapper = lodMapper;
    }

    public List<LodResponse> findByAssetId(UUID assetId) {
        if (!assetRepository.existsById(assetId)) {
            throw new ResourceNotFoundException("Asset", "id", assetId);
        }
        return lodRepository.findByAssetIdOrderByLevelAsc(assetId).stream()
                .map(lodMapper::toResponse)
                .toList();
    }

    @Transactional
    public LodResponse create(UUID assetId, LodRequest request) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", "id", assetId));

        if (lodRepository.existsByAssetIdAndLevel(assetId, request.level())) {
            throw new DuplicateResourceException("Lod", "level", request.level());
        }

        Lod lod = lodMapper.toEntity(request, asset);
        return lodMapper.toResponse(lodRepository.save(lod));
    }

    @Transactional
    public void delete(UUID assetId, UUID lodId) {
        Lod lod = requireLodOfAsset(assetId, lodId);

        if (geometryRepository.existsByLodId(lodId)) {
            throw new ResourceInUseException("Lod", "geometries");
        }
        if (textureSetRepository.existsByLodId(lodId)) {
            throw new ResourceInUseException("Lod", "texture sets");
        }

        lodRepository.delete(lod);
    }

    /**
     * Resolves a LOD and asserts it belongs to the given asset.  A LOD
     * addressed under the wrong parent is reported as not found rather than
     * forbidden, so the nesting does not leak the existence of other assets'
     * LODs.
     */
    private Lod requireLodOfAsset(UUID assetId, UUID lodId) {
        Lod lod = lodRepository.findById(lodId)
                .orElseThrow(() -> new ResourceNotFoundException("Lod", "id", lodId));

        if (!lod.getAsset().getId().equals(assetId)) {
            throw new ResourceNotFoundException("Lod", "id", lodId);
        }
        return lod;
    }
}
