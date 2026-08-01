package com.adb4.rmlmanager.security;

import com.adb4.rmlmanager.entity.Asset;
import com.adb4.rmlmanager.entity.AssetPermission;
import com.adb4.rmlmanager.enums.AssetStatus;
import com.adb4.rmlmanager.enums.PermissionLevel;
import com.adb4.rmlmanager.enums.UserRole;
import com.adb4.rmlmanager.repository.AssetPermissionRepository;
import com.adb4.rmlmanager.repository.AssetRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Central authorization rules for {@link Asset} access.
 *
 * <p>The rules here are the single source of truth for per-asset access
 * decisions. They exist in two layers:
 *
 * <ul>
 *   <li>{@link #isVisibleTo(UUID, Asset)} is the in-memory mirror of
 *       {@code AssetSpecification.visibleTo} and {@code AssetRepository.findAllVisibleTo}:
 *       PUBLISHED, created by the user, or an explicit {@link AssetPermission}.
 *       It deliberately has <strong>no</strong> ADMIN bypass, because the query
 *       side has none either. Changing one without the other will break
 *       {@code AssetAuthorizationServiceTest}.</li>
 *   <li>{@link #canView(AppUserPrincipal, Asset)} and
 *       {@link #canEdit(AppUserPrincipal, Asset)} are the rules callers should
 *       use. They add the ADMIN bypass on top of the visibility rule.</li>
 * </ul>
 *
 * <p>Usable from {@code @PreAuthorize} via the {@link Authentication} overloads:
 * <pre>{@code
 * @PreAuthorize("@assetAuthorizationService.canEdit(authentication, #id)")
 * }</pre>
 *
 * <p>Note that the SpEL overloads return {@code false} rather than throwing when
 * the asset does not exist, which surfaces as 403 and not 404. SpEL wraps
 * exceptions thrown inside an expression, so a {@code ResourceNotFoundException}
 * raised here would not reach the global exception handler intact. Read paths
 * that need to distinguish missing from forbidden should call
 * {@link #canView(AppUserPrincipal, Asset)} directly after loading the asset.
 */
@Service
@Transactional(readOnly = true)
public class AssetAuthorizationService {

    private static final String ADMIN_AUTHORITY = "ROLE_" + UserRole.ADMIN.name();

    private final AssetRepository assetRepository;
    private final AssetPermissionRepository assetPermissionRepository;

    public AssetAuthorizationService(AssetRepository assetRepository,
                                     AssetPermissionRepository assetPermissionRepository) {
        this.assetRepository = assetRepository;
        this.assetPermissionRepository = assetPermissionRepository;
    }

    // ---- primary rules ----

    /**
     * True when the user is an ADMIN, or the asset is visible to them under the
     * same rules the listing queries apply.
     */
    public boolean canView(AppUserPrincipal principal, Asset asset) {
        if (principal == null || asset == null) {
            return false;
        }
        return isAdmin(principal) || isVisibleTo(principal.getId(), asset);
    }

    /**
     * True when the user is an ADMIN, created the asset, or holds an
     * {@link PermissionLevel#EDITOR} permission on it.
     *
     * <p>PUBLISHED status grants read access only and never implies edit access.
     */
    public boolean canEdit(AppUserPrincipal principal, Asset asset) {
        if (principal == null || asset == null) {
            return false;
        }
        if (isAdmin(principal)) {
            return true;
        }
        UUID userId = principal.getId();
        return isCreator(userId, asset)
                || permissionFor(userId, asset)
                .map(permission -> permission.getLevel() == PermissionLevel.EDITOR)
                .orElse(false);
    }

    /**
     * The visibility rule without the ADMIN bypass, mirroring
     * {@code AssetSpecification.visibleTo}. Kept public so parity with the query
     * side can be asserted in tests.
     */
    public boolean isVisibleTo(UUID userId, Asset asset) {
        if (asset == null) {
            return false;
        }
        return asset.getStatus() == AssetStatus.PUBLISHED
                || isCreator(userId, asset)
                || permissionFor(userId, asset).isPresent();
    }

    // ---- @PreAuthorize entry points ----

    public boolean canView(Authentication authentication, UUID assetId) {
        AppUserPrincipal principal = principalOf(authentication);
        return principal != null && canView(principal, loadOrNull(assetId));
    }

    public boolean canEdit(Authentication authentication, UUID assetId) {
        AppUserPrincipal principal = principalOf(authentication);
        return principal != null && canEdit(principal, loadOrNull(assetId));
    }

    // ---- internals ----

    private boolean isAdmin(AppUserPrincipal principal) {
        return principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_AUTHORITY::equals);
    }

    private boolean isCreator(UUID userId, Asset asset) {
        return userId != null && userId.equals(asset.getCreatedBy());
    }

    private Optional<AssetPermission> permissionFor(UUID userId, Asset asset) {
        if (userId == null || asset.getId() == null) {
            return Optional.empty();
        }
        return assetPermissionRepository.findByAppUserIdAndAssetId(userId, asset.getId());
    }

    private AppUserPrincipal principalOf(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getPrincipal() instanceof AppUserPrincipal principal ? principal : null;
    }

    private Asset loadOrNull(UUID assetId) {
        if (assetId == null) {
            return null;
        }
        return assetRepository.findById(assetId).orElse(null);
    }
}