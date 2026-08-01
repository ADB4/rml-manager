package com.adb4.rmlmanager.service;

import com.adb4.rmlmanager.dto.request.VariantRequest;
import com.adb4.rmlmanager.dto.response.VariantResponse;
import com.adb4.rmlmanager.entity.Asset;
import com.adb4.rmlmanager.entity.Variant;
import com.adb4.rmlmanager.exception.DuplicateResourceException;
import com.adb4.rmlmanager.exception.ResourceNotFoundException;
import com.adb4.rmlmanager.mapper.VariantMapper;
import com.adb4.rmlmanager.repository.AssetRepository;
import com.adb4.rmlmanager.repository.VariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class VariantService {

    private final VariantRepository variantRepository;
    private final AssetRepository assetRepository;
    private final VariantMapper variantMapper;

    public VariantService(VariantRepository variantRepository,
                          AssetRepository assetRepository,
                          VariantMapper variantMapper) {
        this.variantRepository = variantRepository;
        this.assetRepository = assetRepository;
        this.variantMapper = variantMapper;
    }

    public List<VariantResponse> findByAssetId(UUID assetId) {
        if (!assetRepository.existsById(assetId)) {
            throw new ResourceNotFoundException("Asset", "id", assetId);
        }
        return variantRepository.findByAssetIdOrderBySortOrderAscCodeAsc(assetId).stream()
                .map(variantMapper::toResponse)
                .toList();
    }

    @Transactional
    public VariantResponse create(UUID assetId, VariantRequest request) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", "id", assetId));

        if (variantRepository.existsByAssetIdAndCode(assetId, request.code())) {
            throw new DuplicateResourceException("Variant", "code", request.code());
        }

        Variant variant = variantMapper.toEntity(request, asset);

        if (Boolean.TRUE.equals(request.isDefault())) {
            clearOtherDefaults(assetId, null);
        }

        return variantMapper.toResponse(variantRepository.save(variant));
    }

    @Transactional
    public VariantResponse update(UUID assetId, UUID variantId, VariantRequest request) {
        Variant variant = requireVariantOfAsset(assetId, variantId);

        if (!variant.getCode().equals(request.code())
                && variantRepository.existsByAssetIdAndCode(assetId, request.code())) {
            throw new DuplicateResourceException("Variant", "code", request.code());
        }

        variantMapper.updateEntity(request, variant);

        if (Boolean.TRUE.equals(request.isDefault())) {
            clearOtherDefaults(assetId, variantId);
        }

        return variantMapper.toResponse(variantRepository.save(variant));
    }

    @Transactional
    public void delete(UUID assetId, UUID variantId) {
        Variant variant = requireVariantOfAsset(assetId, variantId);
        variantRepository.delete(variant);
    }

    /**
     * Resolves a variant and asserts it belongs to the given asset.  A variant
     * addressed under the wrong parent is reported as not found rather than
     * forbidden, so the nesting does not leak the existence of other assets'
     * variants.
     */
    private Variant requireVariantOfAsset(UUID assetId, UUID variantId) {
        Variant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant", "id", variantId));

        if (!variant.getAsset().getId().equals(assetId)) {
            throw new ResourceNotFoundException("Variant", "id", variantId);
        }
        return variant;
    }

    /**
     * Enforces at most one default variant per asset.  The flag is cleared via
     * dirty checking inside the caller's transaction; {@code keepId} is the
     * variant being promoted and is left untouched (null on create, since the
     * new row is not yet persisted).
     */
    private void clearOtherDefaults(UUID assetId, UUID keepId) {
        variantRepository.findByAssetIdAndIsDefaultTrue(assetId).stream()
                .filter(other -> keepId == null || !other.getId().equals(keepId))
                .forEach(other -> other.setIsDefault(false));
    }
}