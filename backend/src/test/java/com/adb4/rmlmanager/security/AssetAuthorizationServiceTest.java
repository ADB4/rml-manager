package com.adb4.rmlmanager.security;

import com.adb4.rmlmanager.entity.AppUser;
import com.adb4.rmlmanager.entity.Asset;
import com.adb4.rmlmanager.entity.AssetPermission;
import com.adb4.rmlmanager.enums.AssetStatus;
import com.adb4.rmlmanager.enums.PermissionLevel;
import com.adb4.rmlmanager.enums.UserRole;
import com.adb4.rmlmanager.repository.AssetPermissionRepository;
import com.adb4.rmlmanager.repository.AssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Branch coverage for the authorization rules.
 *
 * <p>Several tests assert that the permission repository was <em>not</em>
 * consulted. That is not incidental: {@code isVisibleTo} and {@code canEdit}
 * short-circuit on the field reads before reaching the query, so the
 * {@code verify(..., never())} calls pin the short-circuit in place. Strict
 * stubbing enforces the same thing from the other direction — stubbing the
 * repository in one of those tests would fail as an unnecessary stub.
 */
@ExtendWith(MockitoExtension.class)
class AssetAuthorizationServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ASSET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AssetPermissionRepository assetPermissionRepository;

    @InjectMocks
    private AssetAuthorizationService assetAuthorizationService;

    // ---- canView ----

    @Test
    void canView_whenAdmin_returnsTrueForUnrelatedDraft() {
        Asset asset = asset(AssetStatus.DRAFT, OTHER_USER_ID);

        assertTrue(assetAuthorizationService.canView(principal(UserRole.ADMIN), asset));
        verify(assetPermissionRepository, never()).findByAppUserIdAndAssetId(USER_ID, ASSET_ID);
    }

    @Test
    void canView_whenAssetPublished_returnsTrue() {
        Asset asset = asset(AssetStatus.PUBLISHED, OTHER_USER_ID);

        assertTrue(assetAuthorizationService.canView(principal(UserRole.USER), asset));
        verify(assetPermissionRepository, never()).findByAppUserIdAndAssetId(USER_ID, ASSET_ID);
    }

    @Test
    void canView_whenCreatorOfDraft_returnsTrue() {
        Asset asset = asset(AssetStatus.DRAFT, USER_ID);

        assertTrue(assetAuthorizationService.canView(principal(UserRole.USER), asset));
        verify(assetPermissionRepository, never()).findByAppUserIdAndAssetId(USER_ID, ASSET_ID);
    }

    @Test
    void canView_whenViewerPermissionOnDraft_returnsTrue() {
        Asset asset = asset(AssetStatus.DRAFT, OTHER_USER_ID);
        when(assetPermissionRepository.findByAppUserIdAndAssetId(USER_ID, ASSET_ID))
                .thenReturn(Optional.of(permission(PermissionLevel.VIEWER)));

        assertTrue(assetAuthorizationService.canView(principal(UserRole.USER), asset));
    }

    @Test
    void canView_whenEditorPermissionOnDraft_returnsTrue() {
        Asset asset = asset(AssetStatus.DRAFT, OTHER_USER_ID);
        when(assetPermissionRepository.findByAppUserIdAndAssetId(USER_ID, ASSET_ID))
                .thenReturn(Optional.of(permission(PermissionLevel.EDITOR)));

        assertTrue(assetAuthorizationService.canView(principal(UserRole.USER), asset));
    }

    @Test
    void canView_whenUnrelatedUserAndDraft_returnsFalse() {
        Asset asset = asset(AssetStatus.DRAFT, OTHER_USER_ID);
        when(assetPermissionRepository.findByAppUserIdAndAssetId(USER_ID, ASSET_ID))
                .thenReturn(Optional.empty());

        assertFalse(assetAuthorizationService.canView(principal(UserRole.USER), asset));
    }

    @Test
    void canView_whenPrincipalNull_returnsFalse() {
        assertFalse(assetAuthorizationService.canView(null, asset(AssetStatus.PUBLISHED, OTHER_USER_ID)));
    }

    @Test
    void canView_whenAssetNull_returnsFalse() {
        assertFalse(assetAuthorizationService.canView(principal(UserRole.ADMIN), null));
    }

    // ---- canEdit ----

    @Test
    void canEdit_whenAdmin_returnsTrueForUnrelatedDraft() {
        Asset asset = asset(AssetStatus.DRAFT, OTHER_USER_ID);

        assertTrue(assetAuthorizationService.canEdit(principal(UserRole.ADMIN), asset));
        verify(assetPermissionRepository, never()).findByAppUserIdAndAssetId(USER_ID, ASSET_ID);
    }

    @Test
    void canEdit_whenCreator_returnsTrue() {
        Asset asset = asset(AssetStatus.DRAFT, USER_ID);

        assertTrue(assetAuthorizationService.canEdit(principal(UserRole.USER), asset));
        verify(assetPermissionRepository, never()).findByAppUserIdAndAssetId(USER_ID, ASSET_ID);
    }

    @Test
    void canEdit_whenEditorPermission_returnsTrue() {
        Asset asset = asset(AssetStatus.DRAFT, OTHER_USER_ID);
        when(assetPermissionRepository.findByAppUserIdAndAssetId(USER_ID, ASSET_ID))
                .thenReturn(Optional.of(permission(PermissionLevel.EDITOR)));

        assertTrue(assetAuthorizationService.canEdit(principal(UserRole.USER), asset));
    }

    /**
     * The VIEWER/EDITOR distinction. Identical fixture to the test above apart
     * from the enum value, and the opposite outcome.
     */
    @Test
    void canEdit_whenViewerPermission_returnsFalse() {
        Asset asset = asset(AssetStatus.DRAFT, OTHER_USER_ID);
        when(assetPermissionRepository.findByAppUserIdAndAssetId(USER_ID, ASSET_ID))
                .thenReturn(Optional.of(permission(PermissionLevel.VIEWER)));

        assertFalse(assetAuthorizationService.canEdit(principal(UserRole.USER), asset));
    }

    /**
     * Regression guard for the PUBLISHED disjunct, which is present in the
     * visibility rule and deliberately absent from the edit rule. Publishing
     * grants read access to everyone and write access to no one.
     */
    @Test
    void canEdit_whenAssetPublishedButNoRelationship_returnsFalse() {
        Asset asset = asset(AssetStatus.PUBLISHED, OTHER_USER_ID);
        when(assetPermissionRepository.findByAppUserIdAndAssetId(USER_ID, ASSET_ID))
                .thenReturn(Optional.empty());

        assertFalse(assetAuthorizationService.canEdit(principal(UserRole.USER), asset));
    }

    @Test
    void canEdit_whenPrincipalNull_returnsFalse() {
        assertFalse(assetAuthorizationService.canEdit(null, asset(AssetStatus.DRAFT, USER_ID)));
    }

    @Test
    void canEdit_whenAssetNull_returnsFalse() {
        assertFalse(assetAuthorizationService.canEdit(principal(UserRole.ADMIN), null));
    }

    // ---- isVisibleTo ----
    //
    // The rule without the ADMIN bypass. Parity with the actual query lives in
    // AssetAuthorizationParityTest; these cover the branches in isolation.

    @Test
    void isVisibleTo_whenPublished_returnsTrue() {
        assertTrue(assetAuthorizationService.isVisibleTo(USER_ID, asset(AssetStatus.PUBLISHED, OTHER_USER_ID)));
    }

    @Test
    void isVisibleTo_whenCreator_returnsTrue() {
        assertTrue(assetAuthorizationService.isVisibleTo(USER_ID, asset(AssetStatus.DRAFT, USER_ID)));
    }

    @Test
    void isVisibleTo_whenAnyPermissionExists_returnsTrue() {
        when(assetPermissionRepository.findByAppUserIdAndAssetId(USER_ID, ASSET_ID))
                .thenReturn(Optional.of(permission(PermissionLevel.VIEWER)));

        assertTrue(assetAuthorizationService.isVisibleTo(USER_ID, asset(AssetStatus.DRAFT, OTHER_USER_ID)));
    }

    /**
     * Both halves of the deliberate divergence, in one place: the same draft is
     * invisible under the rule that mirrors the query, and viewable by an ADMIN
     * through the bypass layered on top. If the bypass ever migrates down into
     * isVisibleTo, this fails.
     */
    @Test
    void isVisibleTo_hasNoAdminBypass_butCanViewDoes() {
        Asset unrelatedDraft = asset(AssetStatus.DRAFT, OTHER_USER_ID);
        when(assetPermissionRepository.findByAppUserIdAndAssetId(USER_ID, ASSET_ID))
                .thenReturn(Optional.empty());

        assertFalse(assetAuthorizationService.isVisibleTo(USER_ID, unrelatedDraft));
        assertTrue(assetAuthorizationService.canView(principal(UserRole.ADMIN), unrelatedDraft));
    }

    @Test
    void isVisibleTo_whenStatusNull_fallsThroughToOwnershipAndPermission() {
        assertTrue(assetAuthorizationService.isVisibleTo(USER_ID, asset(null, USER_ID)));
    }

    // ---- @PreAuthorize entry points ----

    @Test
    void canView_fromAuthentication_loadsAssetAndDelegates() {
        when(assetRepository.findById(ASSET_ID))
                .thenReturn(Optional.of(asset(AssetStatus.PUBLISHED, OTHER_USER_ID)));

        assertTrue(assetAuthorizationService.canView(authentication(UserRole.USER), ASSET_ID));
    }

    @Test
    void canEdit_fromAuthentication_loadsAssetAndDelegates() {
        when(assetRepository.findById(ASSET_ID))
                .thenReturn(Optional.of(asset(AssetStatus.DRAFT, USER_ID)));

        assertTrue(assetAuthorizationService.canEdit(authentication(UserRole.USER), ASSET_ID));
    }

    @Test
    void canView_fromAuthentication_whenAssetMissing_returnsFalse() {
        when(assetRepository.findById(ASSET_ID)).thenReturn(Optional.empty());

        assertFalse(assetAuthorizationService.canView(authentication(UserRole.ADMIN), ASSET_ID));
    }

    @Test
    void canEdit_fromAuthentication_whenAssetIdNull_returnsFalse() {
        assertFalse(assetAuthorizationService.canEdit(authentication(UserRole.ADMIN), null));
        verify(assetRepository, never()).findById(ASSET_ID);
    }

    @Test
    void canView_fromAuthentication_whenAuthenticationNull_returnsFalse() {
        assertFalse(assetAuthorizationService.canView((Authentication) null, ASSET_ID));
        verify(assetRepository, never()).findById(ASSET_ID);
    }

    @Test
    void canView_fromAuthentication_whenPrincipalIsNotAppUserPrincipal_returnsFalse() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        assertFalse(assetAuthorizationService.canView(anonymous, ASSET_ID));
        verify(assetRepository, never()).findById(ASSET_ID);
    }

    // ---- fixtures ----

    private static Asset asset(AssetStatus status, UUID createdBy) {
        return Asset.builder()
                .id(ASSET_ID)
                .code("CHR-001")
                .title("Oak Chair")
                .status(status)
                .createdBy(createdBy)
                .build();
    }

    private static AssetPermission permission(PermissionLevel level) {
        return AssetPermission.builder()
                .id(UUID.randomUUID())
                .appUserId(USER_ID)
                .assetId(ASSET_ID)
                .level(level)
                .grantedBy(OTHER_USER_ID)
                .build();
    }

    private static AppUserPrincipal principal(UserRole role) {
        return new AppUserPrincipal(AppUser.builder()
                .id(USER_ID)
                .username("tester")
                .password("{noop}secret")
                .role(role)
                .build());
    }

    private static Authentication authentication(UserRole role) {
        AppUserPrincipal principal = principal(role);
        return new UsernamePasswordAuthenticationToken(
                principal, principal.getPassword(), principal.getAuthorities());
    }
}