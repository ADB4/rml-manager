package com.adb4.rmlmanager.service;

import com.adb4.rmlmanager.config.UploadProperties;
import com.adb4.rmlmanager.dto.response.GeometryResponse;
import com.adb4.rmlmanager.entity.Geometry;
import com.adb4.rmlmanager.entity.Lod;
import com.adb4.rmlmanager.entity.MeshPart;
import com.adb4.rmlmanager.enums.GeometryFileType;
import com.adb4.rmlmanager.exception.InvalidUploadException;
import com.adb4.rmlmanager.exception.PayloadTooLargeException;
import com.adb4.rmlmanager.exception.ResourceNotFoundException;
import com.adb4.rmlmanager.mapper.GeometryMapper;
import com.adb4.rmlmanager.repository.GeometryRepository;
import com.adb4.rmlmanager.repository.LodRepository;
import com.adb4.rmlmanager.repository.MeshPartRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Geometry upload with S3 streaming, checksum, and version bookkeeping.
 *
 * <p>The upload is one logical unit of work spanning two systems (S3 and
 * Postgres) that cannot share a transaction. The consistency contract is:
 * a persisted row always points at an object that exists, and a stored object
 * always has a row. This is achieved by (1) rolling back the DB transaction if
 * the S3 write fails, and (2) deleting the S3 object if the DB transaction rolls
 * back after the write (compensating action registered as a transaction
 * synchronization).
 */
@Service
@Transactional(readOnly = true)
public class GeometryService {

    private static final Logger log = LoggerFactory.getLogger(GeometryService.class);

    /** s3_key column is varchar(128); reject composed keys that would not fit. */
    private static final int MAX_KEY_LENGTH = 128;

    private final GeometryRepository geometryRepository;
    private final LodRepository lodRepository;
    private final MeshPartRepository meshPartRepository;
    private final GeometryMapper geometryMapper;
    private final StorageService storageService;
    private final UploadProperties uploadProperties;

    public GeometryService(GeometryRepository geometryRepository,
                           LodRepository lodRepository,
                           MeshPartRepository meshPartRepository,
                           GeometryMapper geometryMapper,
                           StorageService storageService,
                           UploadProperties uploadProperties) {
        this.geometryRepository = geometryRepository;
        this.lodRepository = lodRepository;
        this.meshPartRepository = meshPartRepository;
        this.geometryMapper = geometryMapper;
        this.storageService = storageService;
        this.uploadProperties = uploadProperties;
    }

    /**
     * Streams a geometry file to S3 and records a new latest version.
     *
     * <p>Steps, in order: resolve the LOD and optional mesh part; validate the
     * extension against {@code fileType} and enforce the size cap; compute the
     * next version; stream to S3 while computing SHA-256 in a single pass;
     * register the orphan-cleanup compensation; flip the previous latest row;
     * persist the new row (with {@code isLatest = true} and {@code uploadedBy}
     * filled by {@code @CreatedBy}).
     *
     * @param lodId      target LOD
     * @param fileType   declared geometry file type; must match the file extension
     * @param meshPartId optional mesh part; {@code null} means whole-asset (baked) geometry
     * @param file       multipart file; large files are spooled to disk by the
     *                   servlet multipart resolver, so the body is never fully
     *                   buffered in heap
     * @return the persisted geometry as a response DTO
     */
    @Transactional
    public GeometryResponse upload(UUID lodId, GeometryFileType fileType, UUID meshPartId, MultipartFile file) {
        Lod lod = resolveLod(lodId);
        MeshPart meshPart = resolveMeshPart(meshPartId, lod);

        String fileName = requireFileName(file);
        validateExtension(fileName, fileType);
        enforceMaxSize(file.getSize());

        int nextVersion = nextVersion(lod.getId(), meshPart, fileType);
        String key = buildKey(lod, meshPart, nextVersion, fileName);
        String contentType = contentTypeFor(fileType);

        String checksum = streamToS3(key, file, contentType);

        // The object now exists. If anything below (including commit) fails and
        // the transaction rolls back, delete the object so it is not orphaned.
        registerOrphanCleanup(key);

        flipPreviousLatest(lod.getId(), meshPart, fileType);

        Geometry geometry = Geometry.builder()
                .lod(lod)
                .meshPart(meshPart)
                .fileName(fileName)
                .fileType(fileType)
                .s3Key(key)
                .s3Bucket(storageService.bucket())
                .fileSize(file.getSize())
                .contentType(contentType)
                .version(nextVersion)
                .isLatest(true)
                .checksum(checksum)
                .build(); // uploadedBy populated by @CreatedBy auditing on save
        Geometry saved = geometryRepository.save(geometry);

        log.info("Stored geometry {} (lod={}, meshPart={}, type={}, v{}) at key {}",
                saved.getId(), lod.getId(), meshPartId, fileType, nextVersion, key);
        return geometryMapper.toResponse(saved);
    }

    private Lod resolveLod(UUID lodId) {
        return lodRepository.findById(lodId)
                .orElseThrow(() -> new ResourceNotFoundException("Lod", "id", lodId));
    }

    /**
     * Resolves the optional mesh part and asserts it belongs to the same asset
     * as the LOD. A mesh part from another asset is reported as not found rather
     * than forbidden, so the nesting does not disclose other assets' parts.
     */
    private MeshPart resolveMeshPart(UUID meshPartId, Lod lod) {
        if (meshPartId == null) {
            return null;
        }
        MeshPart meshPart = meshPartRepository.findById(meshPartId)
                .orElseThrow(() -> new ResourceNotFoundException("MeshPart", "id", meshPartId));
        if (!meshPart.getAsset().getId().equals(lod.getAsset().getId())) {
            throw new ResourceNotFoundException("MeshPart", "id", meshPartId);
        }
        return meshPart;
    }

    private String requireFileName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            throw new InvalidUploadException("Uploaded file must have a filename");
        }
        return name;
    }

    /**
     * The extension must be a known {@link GeometryFileType} and must match the
     * declared {@code fileType}, so the stored bytes and the recorded type agree.
     */
    private void validateExtension(String fileName, GeometryFileType declared) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new InvalidUploadException("Filename '" + fileName + "' has no extension");
        }
        String ext = fileName.substring(dot + 1).toUpperCase(Locale.ROOT);
        GeometryFileType fromExtension;
        try {
            fromExtension = GeometryFileType.valueOf(ext);
        } catch (IllegalArgumentException e) {
            throw new InvalidUploadException("Unsupported geometry extension: ." + ext.toLowerCase(Locale.ROOT));
        }
        if (fromExtension != declared) {
            throw new InvalidUploadException(
                    "File extension ." + ext.toLowerCase(Locale.ROOT) + " does not match declared fileType " + declared);
        }
    }

    private void enforceMaxSize(long size) {
        long max = uploadProperties.getMaxGeometrySize().toBytes();
        if (size > max) {
            throw new PayloadTooLargeException("Geometry", size, max);
        }
    }

    /**
     * Next version = current max + 1, computed within this transaction. The null
     * vs non-null mesh part distinction selects the matching query. Concurrent
     * uploads to the same (lod, meshPart, fileType) can race to the same number;
     * a unique constraint on (lod_id, mesh_part_id, file_type, version) plus a
     * retry, or a short pessimistic lock, would harden this if it becomes a
     * concern. Deferred here as an unlikely edge for a single-writer workflow.
     */
    private int nextVersion(UUID lodId, MeshPart meshPart, GeometryFileType fileType) {
        return (meshPart == null)
                ? geometryRepository.findMaxVersionBaked(lodId, fileType) + 1
                : geometryRepository.findMaxVersion(lodId, meshPart.getId(), fileType) + 1;
    }

    /**
     * Clears {@code isLatest} on the current latest row for this
     * (lod, meshPart, fileType) tuple, if any. The change is flushed by dirty
     * checking within the caller's transaction.
     */
    private void flipPreviousLatest(UUID lodId, MeshPart meshPart, GeometryFileType fileType) {
        Optional<Geometry> current = (meshPart == null)
                ? geometryRepository.findByLodIdAndMeshPartIsNullAndFileTypeAndIsLatestTrue(lodId, fileType)
                : geometryRepository.findByLodIdAndMeshPartIdAndFileTypeAndIsLatestTrue(lodId, meshPart.getId(), fileType);
        current.ifPresent(g -> g.setIsLatest(false));
    }

    /**
     * Builds the object key. Extends the documented convention with a
     * {@code v{version}} segment so each version is immutable and addressable —
     * required for downloading non-latest versions by id (KAN-23).
     *
     * <pre>
     * geometry/{assetCode}/lod{level}/v{version}/{fileName}
     * geometry/{assetCode}/lod{level}/part/{meshPartCode}/v{version}/{fileName}
     * </pre>
     */
    private String buildKey(Lod lod, MeshPart meshPart, int version, String fileName) {
        String base = "geometry/" + lod.getAsset().getCode() + "/lod" + lod.getLevel();
        String withPart = (meshPart == null) ? base : base + "/part/" + meshPart.getCode();
        String key = withPart + "/v" + version + "/" + fileName;
        if (key.length() > MAX_KEY_LENGTH) {
            // s3_key is varchar(128); a shorter filename (or a schema widening in
            // a future migration) is needed for pathologically long names.
            throw new InvalidUploadException(
                    "Computed storage key exceeds " + MAX_KEY_LENGTH + " characters; shorten the filename");
        }
        return key;
    }

    private String contentTypeFor(GeometryFileType fileType) {
        return switch (fileType) {
            case GLB -> "model/gltf-binary";
            case GLTF -> "model/gltf+json";
            case OBJ -> "model/obj";
            case FBX, BLEND -> "application/octet-stream";
        };
    }

    /**
     * Streams the file to S3 while computing SHA-256 in the same pass: the SDK
     * reads {@code contentLength} bytes from the {@link DigestInputStream}, which
     * updates the digest as bytes flow through. No second read and no full-file
     * buffer. Returns the lowercase hex checksum.
     */
    private String streamToS3(String key, MultipartFile file, String contentType) {
        MessageDigest digest = newSha256();
        try (InputStream in = file.getInputStream();
             DigestInputStream digesting = new DigestInputStream(in, digest)) {
            storageService.put(key, digesting, file.getSize(), contentType);
        } catch (IOException e) {
            // Opening/reading the multipart stream failed; nothing was persisted
            // and (if put never ran) nothing is in S3. The transaction rolls back.
            throw new UncheckedIOException("Failed to read upload stream for key " + key, e);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM; unreachable in practice.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Deletes the just-written object if the surrounding transaction rolls back
     * (service failure after the put, or a commit failure). Best-effort: a failed
     * cleanup is logged, not rethrown, so it cannot mask the original error.
     */
    private void registerOrphanCleanup(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        storageService.delete(key);
                        log.warn("Transaction rolled back; deleted orphaned S3 object {}", key);
                    } catch (RuntimeException cleanupError) {
                        log.error("Failed to delete orphaned S3 object {} after rollback", key, cleanupError);
                    }
                }
            }
        });
    }
}
