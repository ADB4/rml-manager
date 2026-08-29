package com.adb4.rmlmanager.service;

import com.adb4.rmlmanager.dto.request.CreateTextureSetRequest;
import com.adb4.rmlmanager.dto.response.TextureSetResponse;
import com.adb4.rmlmanager.entity.Lod;
import com.adb4.rmlmanager.entity.MeshPart;
import com.adb4.rmlmanager.entity.TextureSet;
import com.adb4.rmlmanager.entity.Variant;
import com.adb4.rmlmanager.exception.ResourceNotFoundException;
import com.adb4.rmlmanager.mapper.TextureSetMapper;
import com.adb4.rmlmanager.repository.LodRepository;
import com.adb4.rmlmanager.repository.MeshPartRepository;
import com.adb4.rmlmanager.repository.TextureSetRepository;
import com.adb4.rmlmanager.repository.VariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Texture set creation with the same version bookkeeping as geometry uploads.
 *
 * <p>A set is scoped to a variant plus an optional LOD and optional mesh part.
 * Versions count within that (variant, lod, meshPart) scope: the new set gets
 * the next version number and becomes the latest, and the previous latest in
 * the scope is flipped — mirroring {@link GeometryService}'s semantics for the
 * (lod, meshPart, fileType) tuple.
 */
@Service
@Transactional(readOnly = true)
public class TextureSetService {

    private final TextureSetRepository textureSetRepository;
    private final VariantRepository variantRepository;
    private final LodRepository lodRepository;
    private final MeshPartRepository meshPartRepository;
    private final TextureSetMapper textureSetMapper;

    public TextureSetService(TextureSetRepository textureSetRepository,
                             VariantRepository variantRepository,
                             LodRepository lodRepository,
                             MeshPartRepository meshPartRepository,
                             TextureSetMapper textureSetMapper) {
        this.textureSetRepository = textureSetRepository;
        this.variantRepository = variantRepository;
        this.lodRepository = lodRepository;
        this.meshPartRepository = meshPartRepository;
        this.textureSetMapper = textureSetMapper;
    }

    @Transactional
    public TextureSetResponse create(UUID variantId, CreateTextureSetRequest request) {
        Variant variant = resolveVariant(variantId);
        Lod lod = resolveLod(request.lodId(), variant);
        MeshPart meshPart = resolveMeshPart(request.meshPartId(), variant);

        List<TextureSet> scoped = findScopedSets(variantId, request.lodId(), request.meshPartId());
        int nextVersion = highestVersion(scoped) + 1;
        flipPreviousLatest(scoped);

        TextureSet textureSet = TextureSet.builder()
                .variant(variant)
                .lod(lod)
                .meshPart(meshPart)
                .version(nextVersion)
                .isLatest(true)
                .build();
        return textureSetMapper.toResponse(textureSetRepository.save(textureSet));
    }

    private Variant resolveVariant(UUID variantId) {
        return variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant", "id", variantId));
    }

    /**
     * Resolves the optional LOD and asserts it belongs to the same asset as the
     * variant. A LOD from another asset is reported as not found rather than
     * forbidden, so the nesting does not disclose other assets' LODs.
     */
    private Lod resolveLod(UUID lodId, Variant variant) {
        if (lodId == null) {
            return null;
        }
        Lod lod = lodRepository.findById(lodId)
                .orElseThrow(() -> new ResourceNotFoundException("Lod", "id", lodId));
        if (!lod.getAsset().getId().equals(variant.getAsset().getId())) {
            throw new ResourceNotFoundException("Lod", "id", lodId);
        }
        return lod;
    }

    /**
     * Resolves the optional mesh part with the same same-asset assertion as
     * {@link #resolveLod(UUID, Variant)}.
     */
    private MeshPart resolveMeshPart(UUID meshPartId, Variant variant) {
        if (meshPartId == null) {
            return null;
        }
        MeshPart meshPart = meshPartRepository.findById(meshPartId)
                .orElseThrow(() -> new ResourceNotFoundException("MeshPart", "id", meshPartId));
        if (!meshPart.getAsset().getId().equals(variant.getAsset().getId())) {
            throw new ResourceNotFoundException("MeshPart", "id", meshPartId);
        }
        return meshPart;
    }

    /**
     * Loads the variant's sets and narrows to the (lod, meshPart) scope in
     * memory. Two independently nullable columns would otherwise need four
     * derived-query permutations; a variant's set count is small, so one query
     * plus a filter is the simpler shape.
     */
    private List<TextureSet> findScopedSets(UUID variantId, UUID lodId, UUID meshPartId) {
        return textureSetRepository.findByVariantId(variantId).stream()
                .filter(set -> sameScope(set, lodId, meshPartId))
                .toList();
    }

    private static boolean sameScope(TextureSet set, UUID lodId, UUID meshPartId) {
        UUID setLodId = set.getLod() == null ? null : set.getLod().getId();
        UUID setMeshPartId = set.getMeshPart() == null ? null : set.getMeshPart().getId();
        return Objects.equals(setLodId, lodId) && Objects.equals(setMeshPartId, meshPartId);
    }

    /**
     * Highest existing version in the scope, 0 when the scope is empty — the
     * in-memory equivalent of {@code GeometryRepository}'s COALESCE(MAX, 0)
     * queries. Concurrent creates in the same scope can race to the same
     * number; deferred as an unlikely edge for a single-writer workflow, as in
     * {@code GeometryService}.
     */
    private static int highestVersion(List<TextureSet> scoped) {
        return scoped.stream()
                .map(TextureSet::getVersion)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
    }

    /**
     * Clears {@code isLatest} on the current latest set in the scope, if any.
     * The change is flushed by dirty checking within the caller's transaction.
     */
    private static void flipPreviousLatest(List<TextureSet> scoped) {
        scoped.stream()
                .filter(set -> Boolean.TRUE.equals(set.getIsLatest()))
                .forEach(set -> set.setIsLatest(false));
    }
}
