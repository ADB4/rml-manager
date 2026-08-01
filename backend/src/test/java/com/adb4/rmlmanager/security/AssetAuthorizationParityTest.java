package com.adb4.rmlmanager.security;

import com.adb4.rmlmanager.TestcontainersConfiguration;
import com.adb4.rmlmanager.entity.AppUser;
import com.adb4.rmlmanager.entity.Asset;
import com.adb4.rmlmanager.entity.AssetPermission;
import com.adb4.rmlmanager.entity.Category;
import com.adb4.rmlmanager.entity.Subcategory;
import com.adb4.rmlmanager.enums.AssetStatus;
import com.adb4.rmlmanager.enums.PermissionLevel;
import com.adb4.rmlmanager.enums.UserRole;
import com.adb4.rmlmanager.repository.AssetPermissionRepository;
import com.adb4.rmlmanager.repository.AssetRepository;
import com.adb4.rmlmanager.repository.AssetSpecification;
import com.adb4.rmlmanager.repository.CategoryRepository;
import com.adb4.rmlmanager.repository.SubcategoryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Closes AC 4: the rules in {@link AssetAuthorizationService} must match the
 * semantics of {@code findAllVisibleTo}.
 *
 * <p>{@code AssetAuthorizationServiceTest} asserts the rule as written, which
 * means a change to {@code AssetSpecification.visibleTo} alone would not fail
 * anything. This test runs the real query against a real Postgres and compares
 * its result set against {@link AssetAuthorizationService#isVisibleTo}, so the
 * two cannot drift apart silently.
 *
 * <p>Uses {@code @SpringBootTest} rather than {@code @DataJpaTest} to reuse the
 * proven {@link TestcontainersConfiguration} wiring and to keep JPA auditing
 * active — {@code Auditable.createdAt} is {@code nullable = false} and lives on
 * a {@code @MappedSuperclass}, so Lombok's {@code @Builder} on the entities
 * cannot set it. {@code @DataJpaTest} would be faster but does not import
 * {@code JpaAuditingConfig}, which would leave {@code createdAt} null.
 *
 * <p>No {@code AppUser} rows are seeded: {@code assets.created_by} and
 * {@code asset_permissions.app_user_id} are plain UUID columns with no foreign
 * key to {@code app_users}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AssetAuthorizationParityTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetPermissionRepository assetPermissionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SubcategoryRepository subcategoryRepository;

    @Autowired
    private AssetAuthorizationService assetAuthorizationService;

    private Asset published;
    private Asset ownedDraft;
    private Asset permittedDraft;
    private Asset unrelatedDraft;

    @BeforeEach
    void seed() {
        Category category = categoryRepository.save(Category.builder().name("Furniture").build());
        Subcategory subcategory = subcategoryRepository.save(
                Subcategory.builder().category(category).name("Chairs").build());

        published = assetRepository.save(asset("PUB-001", "Published Chair",
                subcategory, AssetStatus.PUBLISHED, OTHER_USER_ID));
        ownedDraft = assetRepository.save(asset("OWN-001", "My Draft Chair",
                subcategory, AssetStatus.DRAFT, USER_ID));
        permittedDraft = assetRepository.save(asset("PRM-001", "Shared Draft Chair",
                subcategory, AssetStatus.DRAFT, OTHER_USER_ID));
        unrelatedDraft = assetRepository.save(asset("HID-001", "Someone Else's Draft",
                subcategory, AssetStatus.DRAFT, OTHER_USER_ID));

        assetPermissionRepository.save(AssetPermission.builder()
                .appUserId(USER_ID)
                .assetId(permittedDraft.getId())
                .level(PermissionLevel.VIEWER)
                .grantedBy(OTHER_USER_ID)
                .build());

        entityManager.flush();
        entityManager.clear();
    }

    /**
     * The core parity assertion. For every seeded asset, the in-memory rule and
     * the SQL predicate must agree.
     */
    @Test
    void isVisibleTo_agreesWithSpecificationForEveryAsset() {
        Set<UUID> fromQuery = idsOf(assetRepository.findAll(AssetSpecification.visibleTo(USER_ID)));

        for (Asset asset : assetRepository.findAll()) {
            boolean fromRule = assetAuthorizationService.isVisibleTo(USER_ID, asset);
            assertEquals(fromQuery.contains(asset.getId()), fromRule,
                    "Rule and query disagree on asset " + asset.getCode()
                            + " (query says " + fromQuery.contains(asset.getId())
                            + ", isVisibleTo says " + fromRule + ")");
        }
    }

    @Test
    void specificationReturnsExactlyPublishedOwnedAndPermitted() {
        Set<UUID> visible = idsOf(assetRepository.findAll(AssetSpecification.visibleTo(USER_ID)));

        assertEquals(
                Set.of(published.getId(), ownedDraft.getId(), permittedDraft.getId()),
                visible);
    }

    /**
     * The JPQL named query and the Specification are two encodings of the same
     * rule and must also agree with each other.
     */
    @Test
    void findAllVisibleToAgreesWithSpecification() {
        Set<UUID> fromJpql = idsOf(assetRepository.findAllVisibleTo(USER_ID, Pageable.unpaged()).getContent());
        Set<UUID> fromSpec = idsOf(assetRepository.findAll(AssetSpecification.visibleTo(USER_ID)));

        assertEquals(fromSpec, fromJpql);
    }

    /**
     * Documents the known divergence rather than leaving it implicit: an ADMIN
     * can view a draft that the listing query will not return. Recorded on
     * KAN-26. If {@code AssetSpecification.visibleTo} ever grows an admin
     * branch, this test is the one that should be deleted.
     */
    @Test
    void adminCanViewAssetThatTheListingQueryExcludes() {
        Set<UUID> visibleToAdminUser = idsOf(assetRepository.findAll(AssetSpecification.visibleTo(USER_ID)));
        Asset hidden = assetRepository.findById(unrelatedDraft.getId()).orElseThrow();

        assertFalse(visibleToAdminUser.contains(hidden.getId()));
        assertTrue(assetAuthorizationService.canView(principal(UserRole.ADMIN), hidden));
    }

    @Test
    void canEditDistinguishesViewerFromEditor() {
        Asset shared = assetRepository.findById(permittedDraft.getId()).orElseThrow();
        AppUserPrincipal user = principal(UserRole.USER);

        assertTrue(assetAuthorizationService.canView(user, shared));
        assertFalse(assetAuthorizationService.canEdit(user, shared));

        AssetPermission permission = assetPermissionRepository
                .findByAppUserIdAndAssetId(USER_ID, shared.getId()).orElseThrow();
        permission.setLevel(PermissionLevel.EDITOR);
        assetPermissionRepository.save(permission);
        entityManager.flush();
        entityManager.clear();

        assertTrue(assetAuthorizationService.canEdit(user, shared));
    }

    // ---- fixtures ----

    private static Asset asset(String code, String title, Subcategory subcategory,
                               AssetStatus status, UUID createdBy) {
        return Asset.builder()
                .code(code)
                .title(title)
                .subcategory(subcategory)
                .status(status)
                .hasAnimation(false)
                .createdBy(createdBy)
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

    private static Set<UUID> idsOf(List<Asset> assets) {
        return assets.stream().map(Asset::getId).collect(Collectors.toSet());
    }
}