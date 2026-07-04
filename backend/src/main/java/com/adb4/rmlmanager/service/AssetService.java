package com.adb4.rmlmanager.service;

import com.adb4.rmlmanager.dto.request.CreateAssetRequest;
import com.adb4.rmlmanager.dto.request.UpdateAssetRequest;
import com.adb4.rmlmanager.dto.response.AssetSummaryResponse;
import com.adb4.rmlmanager.entity.Asset;
import com.adb4.rmlmanager.entity.Subcategory;
import com.adb4.rmlmanager.exception.DuplicateResourceException;
import com.adb4.rmlmanager.exception.ResourceNotFoundException;
import com.adb4.rmlmanager.mapper.AssetMapper;
import com.adb4.rmlmanager.repository.AssetRepository;
import com.adb4.rmlmanager.repository.SubcategoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AssetService {

    private final AssetRepository assetRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final AssetMapper assetMapper;

    public AssetService(AssetRepository assetRepository,
                        SubcategoryRepository subcategoryRepository,
                        AssetMapper assetMapper) {
        this.assetRepository = assetRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.assetMapper = assetMapper;
    }

    public Page<AssetSummaryResponse> findAllVisible(UUID userId, Pageable pageable) {
        return assetRepository.findAllVisibleTo(userId, pageable)
                .map(assetMapper::toSummaryResponse);
    }

    public AssetSummaryResponse findByCode(String code) {
        Asset asset = assetRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", "code", code));
        return assetMapper.toSummaryResponse(asset);
    }

    @Transactional
    public AssetSummaryResponse create(CreateAssetRequest request) {
        if (assetRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException("Asset", "code", request.code());
        }
        Subcategory subcategory = resolveSubcategory(request.subcategoryId());
        Asset asset = assetMapper.toEntity(request, subcategory);
        return assetMapper.toSummaryResponse(assetRepository.save(asset));
    }

    @Transactional
    public AssetSummaryResponse update(UUID assetId, UpdateAssetRequest request) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", "id", assetId));
        Subcategory subcategory = resolveSubcategory(request.subcategoryId());
        assetMapper.updateEntity(request, subcategory, asset);
        return assetMapper.toSummaryResponse(assetRepository.save(asset));
    }

    private Subcategory resolveSubcategory(UUID subcategoryId) {
        return subcategoryRepository.findById(subcategoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Subcategory", "id", subcategoryId));
    }
}