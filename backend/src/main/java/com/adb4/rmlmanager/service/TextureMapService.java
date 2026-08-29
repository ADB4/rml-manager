package com.adb4.rmlmanager.service;

import com.adb4.rmlmanager.config.UploadProperties;
import com.adb4.rmlmanager.dto.response.TextureMapResponse;
import com.adb4.rmlmanager.entity.TextureMap;
import com.adb4.rmlmanager.entity.TextureSet;
import com.adb4.rmlmanager.entity.Variant;
import com.adb4.rmlmanager.enums.TextureFileType;
import com.adb4.rmlmanager.enums.TextureMapType;
import com.adb4.rmlmanager.exception.DuplicateResourceException;
import com.adb4.rmlmanager.exception.InvalidUploadException;
import com.adb4.rmlmanager.exception.PayloadTooLargeException;
import com.adb4.rmlmanager.exception.ResourceNotFoundException;
import com.adb4.rmlmanager.mapper.TextureMapMapper;
import com.adb4.rmlmanager.repository.TextureMapRepository;
import com.adb4.rmlmanager.repository.TextureSetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Texture map upload with checksum deduplication, plus set membership
 * management.
 *
 * <p>Uploads follow the S3-before-DB consistency pattern established in
 * {@link GeometryService}, with a dedup twist: the SHA-256 checksum is computed
 * in a first pass over the multipart file (spooled to disk by the servlet
 * resolver, so this re-reads a temp file, not the network stream) and
 * {@code findByChecksum} is consulted <em>before</em> anything is written to
 * S3. On a match the existing {@link TextureMap} row is linked to the target
 * set and no new object is stored.
 *
 * <p>Because rows and objects are shared across sets this way, removing a map
 * from a set only deletes the association — never the row or the object.
 */
@Service
@Transactional(readOnly = true)
public class TextureMapService {

    private static final Logger log = LoggerFactory.getLogger(TextureMapService.class);

    /** s3_key column defaults to varchar(255); reject composed keys that would not fit. */
    private static final int MAX_KEY_LENGTH = 255;

    /** Hex characters of the SHA-256 checksum used as an object-key segment (64 bits). */
    private static final int KEY_CHECKSUM_LENGTH = 16;

    private final TextureSetRepository textureSetRepository;
    private final TextureMapRepository textureMapRepository;
    private final TextureMapMapper textureMapMapper;
    private final StorageService storageService;
    private final UploadProperties uploadProperties;

    public TextureMapService(TextureSetRepository textureSetRepository,
                             TextureMapRepository textureMapRepository,
                             TextureMapMapper textureMapMapper,
                             StorageService storageService,
                             UploadProperties uploadProperties) {
        this.textureSetRepository = textureSetRepository;
        this.textureMapRepository = textureMapRepository;
        this.textureMapMapper = textureMapMapper;
        this.storageService = storageService;
        this.uploadProperties = uploadProperties;
    }

    /**
     * Uploads a texture map into a set, deduplicating identical content.
     *
     * <p>Steps, in order: resolve the set; derive and validate the file type
     * from the extension; enforce the size cap; compute the checksum; then
     * either link the existing map with that checksum into the set, or extract
     * dimensions, stream the file to S3 (with orphan-cleanup compensation),
     * persist a new row (with {@code uploadedBy} filled by {@code @CreatedBy}),
     * and link it.
     *
     * @param textureSetId target texture set
     * @param type         semantic role of the map within the set (albedo, normal, ...)
     * @param file         multipart file; the extension determines the {@link TextureFileType}
     * @return the linked map as a response DTO
     */
    @Transactional
    public TextureMapResponse upload(UUID textureSetId, TextureMapType type, MultipartFile file) {
        TextureSet textureSet = resolveSet(textureSetId);

        String fileName = requireFileName(file);
        TextureFileType fileType = fileTypeFromExtension(fileName);
        enforceMaxSize(file.getSize());

        String checksum = computeChecksum(file);

        Optional<TextureMap> existing = textureMapRepository.findByChecksum(checksum);
        return existing
                .map(match -> linkExisting(textureSet, match, type, checksum))
                .orElseGet(() -> storeNew(textureSet, type, fileName, fileType, checksum, file));
    }

    /**
     * Removes a map from a set — the association only. The map row and its S3
     * object are always retained: checksum dedup means either may be shared
     * with other sets, and an unreferenced map can still be relinked by a later
     * upload of the same content.
     */
    @Transactional
    public void removeFromSet(UUID textureSetId, UUID textureMapId) {
        TextureSet textureSet = resolveSet(textureSetId);
        TextureMap textureMap = textureSet.getTextureMaps().stream()
                .filter(candidate -> candidate.getId().equals(textureMapId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("TextureMap", "id", textureMapId));
        // join row deleted by dirty checking at flush; the map row and object stay
        textureSet.getTextureMaps().remove(textureMap);
    }

    /**
     * Links an already-stored map into the set instead of storing a duplicate
     * object. The declared type must agree with the stored row: silently
     * reusing, say, an ALBEDO row for a NORMAL upload would misrepresent the
     * caller's intent.
     */
    private TextureMapResponse linkExisting(TextureSet textureSet, TextureMap existing,
                                            TextureMapType declared, String checksum) {
        if (existing.getType() != declared) {
            throw new InvalidUploadException("File content matches existing texture map " + existing.getId()
                    + " of type " + existing.getType() + ", not the declared type " + declared);
        }
        if (textureSet.getTextureMaps().contains(existing)) {
            throw new DuplicateResourceException("TextureMap", "checksum", checksum);
        }
        // join row inserted by dirty checking at flush
        textureSet.getTextureMaps().add(existing);
        log.info("Linked existing texture map {} (checksum {}) to set {}",
                existing.getId(), checksum, textureSet.getId());
        return textureMapMapper.toResponse(existing);
    }

    private TextureMapResponse storeNew(TextureSet textureSet, TextureMapType type, String fileName,
                                        TextureFileType fileType, String checksum, MultipartFile file) {
        ImageDimensions dimensions = extractDimensions(file);
        String key = buildKey(textureSet, type, checksum, fileName);
        String contentType = contentTypeFor(fileType);

        streamToS3(key, file, contentType);

        // The object now exists. If anything below (including commit) fails and
        // the transaction rolls back, delete the object so it is not orphaned.
        registerOrphanCleanup(key);

        TextureMap textureMap = TextureMap.builder()
                .type(type)
                .fileName(fileName)
                .fileType(fileType)
                .s3Key(key)
                .s3Bucket(storageService.bucket())
                // file_size is a 32-bit INTEGER column; the size cap keeps this in range
                .fileSize(Math.toIntExact(file.getSize()))
                .width(dimensions.width())
                .height(dimensions.height())
                .checksum(checksum)
                .build(); // uploadedBy populated by @CreatedBy auditing on save
        TextureMap saved = textureMapRepository.save(textureMap);
        textureSet.getTextureMaps().add(saved);

        log.info("Stored texture map {} (set={}, type={}, {}x{}) at key {}",
                saved.getId(), textureSet.getId(), type, dimensions.width(), dimensions.height(), key);
        return textureMapMapper.toResponse(saved);
    }

    private TextureSet resolveSet(UUID textureSetId) {
        return textureSetRepository.findById(textureSetId)
                .orElseThrow(() -> new ResourceNotFoundException("TextureSet", "id", textureSetId));
    }

    private String requireFileName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            throw new InvalidUploadException("Uploaded file must have a filename");
        }
        return name;
    }

    /**
     * Derives the {@link TextureFileType} from the file extension. Unlike
     * geometry uploads there is no separately declared file type to cross-check;
     * the extension is the single source and must name a supported format.
     */
    private TextureFileType fileTypeFromExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new InvalidUploadException("Filename '" + fileName + "' has no extension");
        }
        String ext = fileName.substring(dot + 1).toUpperCase(Locale.ROOT);
        try {
            return TextureFileType.valueOf(ext);
        } catch (IllegalArgumentException e) {
            throw new InvalidUploadException("Unsupported texture extension: ." + ext.toLowerCase(Locale.ROOT));
        }
    }

    private void enforceMaxSize(long size) {
        long max = uploadProperties.getMaxTextureSize().toBytes();
        if (size > max) {
            throw new PayloadTooLargeException("TextureMap", size, max);
        }
    }

    /**
     * Reads the multipart file once to compute its SHA-256 before any S3 write,
     * so deduplication can be decided without storing anything. This is the
     * point of divergence from {@code GeometryService}, which digests during
     * the upload stream instead.
     */
    private String computeChecksum(MultipartFile file) {
        MessageDigest digest = newSha256();
        try (DigestInputStream digesting = new DigestInputStream(file.getInputStream(), digest)) {
            digesting.transferTo(OutputStream.nullOutputStream());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload stream while computing checksum", e);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Reads the image header to extract pixel dimensions without decoding the
     * full image. Best-effort: formats with no registered ImageIO reader (SVG,
     * WEBP, and AVIF on a stock JDK) and unreadable content leave the
     * dimensions null rather than failing the upload.
     */
    private ImageDimensions extractDimensions(MultipartFile file) {
        try (InputStream in = file.getInputStream();
             ImageInputStream imageIn = ImageIO.createImageInputStream(in)) {
            if (imageIn == null) {
                return ImageDimensions.UNKNOWN;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageIn);
            if (!readers.hasNext()) {
                return ImageDimensions.UNKNOWN;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageIn, true, true);
                return new ImageDimensions(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            log.warn("Could not extract image dimensions from {}: {}",
                    file.getOriginalFilename(), e.getMessage());
            return ImageDimensions.UNKNOWN;
        }
    }

    /**
     * Builds the object key. Extends the documented convention in two ways: the
     * {@code lod{level}} segment is omitted for sets shared across all LOD
     * levels, and a checksum segment keeps distinct contents with the same
     * filename from overwriting each other — maps carry no version number, so
     * the checksum is what makes each stored object immutable and addressable.
     *
     * <pre>
     * texture/{assetCode}/variant/{variantCode}[/lod{level}][/part/{meshPartCode}]/{mapType}/{checksum16}/{fileName}
     * </pre>
     */
    private String buildKey(TextureSet textureSet, TextureMapType type, String checksum, String fileName) {
        Variant variant = textureSet.getVariant();
        String base = "texture/" + variant.getAsset().getCode() + "/variant/" + variant.getCode();
        String withLod = (textureSet.getLod() == null)
                ? base
                : base + "/lod" + textureSet.getLod().getLevel();
        String withPart = (textureSet.getMeshPart() == null)
                ? withLod
                : withLod + "/part/" + textureSet.getMeshPart().getCode();
        String key = withPart + "/" + type.name().toLowerCase(Locale.ROOT)
                + "/" + checksum.substring(0, KEY_CHECKSUM_LENGTH) + "/" + fileName;
        if (key.length() > MAX_KEY_LENGTH) {
            throw new InvalidUploadException(
                    "Computed storage key exceeds " + MAX_KEY_LENGTH + " characters; shorten the filename");
        }
        return key;
    }

    private String contentTypeFor(TextureFileType fileType) {
        return switch (fileType) {
            case JPG, JPEG -> "image/jpeg";
            case PNG -> "image/png";
            case GIF -> "image/gif";
            case SVG -> "image/svg+xml";
            case WEBP -> "image/webp";
            case AVIF -> "image/avif";
            case TIFF, TIF -> "image/tiff";
        };
    }

    private void streamToS3(String key, MultipartFile file, String contentType) {
        try (InputStream in = file.getInputStream()) {
            storageService.put(key, in, file.getSize(), contentType);
        } catch (IOException e) {
            // Opening/reading the multipart stream failed; nothing was persisted
            // and (if put never ran) nothing is in S3. The transaction rolls back.
            throw new UncheckedIOException("Failed to read upload stream for key " + key, e);
        }
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
     * Mirrors {@code GeometryService}'s compensation.
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

    /** Pixel dimensions read from an image header; both null when unavailable. */
    private record ImageDimensions(Integer width, Integer height) {
        static final ImageDimensions UNKNOWN = new ImageDimensions(null, null);
    }
}
