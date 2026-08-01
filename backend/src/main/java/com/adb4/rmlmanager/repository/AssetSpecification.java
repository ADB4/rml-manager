package com.adb4.rmlmanager.repository;

import com.adb4.rmlmanager.entity.Asset;
import com.adb4.rmlmanager.entity.AssetPermission;
import com.adb4.rmlmanager.enums.AssetStatus;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

/**
 * Composable JPA specifications for asset queries.
 *
 * <p>Every filtered listing must include {@link #visibleTo(UUID)} to enforce
 * the same visibility semantics as the original {@code findAllVisibleTo} JPQL:
 * an asset is visible if it is PUBLISHED, owned by the current user, or the
 * user has an explicit {@link AssetPermission}.
 *
 * <p>Call {@link #fetchSubcategoryAndCategory()} in the composition to avoid
 * N+1 on the subcategory/category associations.  It is safe to combine with
 * the other specifications; the fetch is skipped for count queries
 * automatically.
 */
public final class AssetSpecification {

    private AssetSpecification() {
    }

    /**
     * Restricts results to assets visible to the given user:
     * PUBLISHED, created by the user, or explicitly permitted.
     */
    public static Specification<Asset> visibleTo(UUID userId) {
        return (root, query, cb) -> {
            Predicate published = cb.equal(root.get("status"), AssetStatus.PUBLISHED);
            Predicate owned = cb.equal(root.get("createdBy"), userId);

            Subquery<Long> permissionSubquery = query.subquery(Long.class);
            Root<AssetPermission> perm = permissionSubquery.from(AssetPermission.class);
            permissionSubquery.select(cb.literal(1L))
                    .where(cb.and(
                            cb.equal(perm.get("assetId"), root.get("id")),
                            cb.equal(perm.get("appUserId"), userId)
                    ));

            Predicate permitted = cb.exists(permissionSubquery);

            return cb.or(published, owned, permitted);
        };
    }

    public static Specification<Asset> inCategory(UUID categoryId) {
        return (root, query, cb) ->
                cb.equal(root.get("subcategory").get("category").get("id"), categoryId);
    }

    public static Specification<Asset> inSubcategory(UUID subcategoryId) {
        return (root, query, cb) ->
                cb.equal(root.get("subcategory").get("id"), subcategoryId);
    }

    public static Specification<Asset> withStatus(AssetStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Asset> titleContains(String fragment) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("title")),
                        "%" + fragment.toLowerCase() + "%");
    }

    /**
     * Eagerly fetches the subcategory and its parent category to prevent N+1.
     * Automatically skipped for count queries.
     */
    public static Specification<Asset> fetchSubcategoryAndCategory() {
        return (root, query, cb) -> {
            if (!Long.class.equals(query.getResultType())
                    && !long.class.equals(query.getResultType())) {
                root.fetch("subcategory", JoinType.LEFT)
                        .fetch("category", JoinType.LEFT);
            }
            return cb.conjunction();
        };
    }
}