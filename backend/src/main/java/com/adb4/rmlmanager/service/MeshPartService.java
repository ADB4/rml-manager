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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MeshPartService {

    private final MeshPartRepository meshPartRepository;
    private final AssetRepository assetRepository;
    private final GeometryRepository geometryRepository;
    private final TextureSetRepository textureSetRepository;
    private final MeshPartMapper meshPartMapper;

    public MeshPartService(MeshPartRepository meshPartRepository,
                           AssetRepository assetRepository,
                           GeometryRepository geometryRepository,
                           TextureSetRepository textureSetRepository,
                           MeshPartMapper meshPartMapper) {
        this.meshPartRepository = meshPartRepository;
        this.assetRepository = assetRepository;
        this.geometryRepository = geometryRepository;
        this.textureSetRepository = textureSetRepository;
        this.meshPartMapper = meshPartMapper;
    }

    public List<MeshPartResponse> findByAssetId(UUID assetId) {
        if (!assetRepository.existsById(assetId)) {
            throw new ResourceNotFoundException("Asset", "id", assetId);
        }
        return meshPartRepository.findByAssetIdOrderByCodeAsc(assetId).stream()
                .map(meshPartMapper::toResponse)
                .toList();
    }

    @Transactional
    public MeshPartResponse create(UUID assetId, MeshPartRequest request) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", "id", assetId));

        if (meshPartRepository.existsByAssetIdAndCode(assetId, request.code())) {
            throw new DuplicateResourceException("MeshPart", "code", request.code());
        }

        MeshPart meshPart = meshPartMapper.toEntity(request, asset);
        return meshPartMapper.toResponse(meshPartRepository.save(meshPart));
    }

    @Transactional
    public MeshPartResponse update(UUID assetId, UUID meshPartId, MeshPartRequest request) {
        MeshPart meshPart = requireMeshPartOfAsset(assetId, meshPartId);

        if (!meshPart.getCode().equals(request.code())
                && meshPartRepository.existsByAssetIdAndCode(assetId, request.code())) {
            throw new DuplicateResourceException("MeshPart", "code", request.code());
        }

        meshPartMapper.updateEntity(request, meshPart);
        return meshPartMapper.toResponse(meshPartRepository.save(meshPart));
    }

    @Transactional
    public void delete(UUID assetId, UUID meshPartId) {
        MeshPart meshPart = requireMeshPartOfAsset(assetId, meshPartId);

        if (geometryRepository.existsByMeshPartId(meshPartId)) {
            throw new ResourceInUseException("MeshPart", "geometries");
        }
        if (textureSetRepository.existsByMeshPartId(meshPartId)) {
            throw new ResourceInUseException("MeshPart", "texture sets");
        }

        meshPartRepository.delete(meshPart);
    }

    /**
     * Resolves a mesh part and asserts it belongs to the given asset.  A mesh
     * part addressed under the wrong parent is reported as not found rather
     * than forbidden, so the nesting does not leak the existence of other
     * assets' mesh parts.
     */
    private MeshPart requireMeshPartOfAsset(UUID assetId, UUID meshPartId) {
        MeshPart meshPart = meshPartRepository.findById(meshPartId)
                .orElseThrow(() -> new ResourceNotFoundException("MeshPart", "id", meshPartId));

        if (!meshPart.getAsset().getId().equals(assetId)) {
            throw new ResourceNotFoundException("MeshPart", "id", meshPartId);
        }
        return meshPart;
    }
}
