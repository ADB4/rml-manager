package com.adb4.rmlmanager.service;

import com.adb4.rmlmanager.TestcontainersConfiguration;
import com.adb4.rmlmanager.dto.request.SubcategoryRequest;
import com.adb4.rmlmanager.dto.response.SubcategoryResponse;
import com.adb4.rmlmanager.entity.Asset;
import com.adb4.rmlmanager.entity.Category;
import com.adb4.rmlmanager.entity.Subcategory;
import com.adb4.rmlmanager.enums.AssetStatus;
import com.adb4.rmlmanager.exception.DuplicateResourceException;
import com.adb4.rmlmanager.exception.ResourceInUseException;
import com.adb4.rmlmanager.exception.ResourceNotFoundException;
import com.adb4.rmlmanager.repository.AssetRepository;
import com.adb4.rmlmanager.repository.CategoryRepository;
import com.adb4.rmlmanager.repository.SubcategoryRepository;
import com.adb4.rmlmanager.repository.AppUserRepository;
import com.adb4.rmlmanager.security.AppUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SubcategoryServiceRepositoryIT {

    @Autowired
    private SubcategoryService subcategoryService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SubcategoryRepository subcategoryRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    private UUID furnitureId;
    private UUID lightingId;

    @BeforeEach
    void setUp() {
        var admin = appUserRepository.findByUsername("admin").orElseThrow();
        var principal = new AppUserPrincipal(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        Category furniture = categoryRepository.save(Category.builder().name("Furniture").build());
        furnitureId = furniture.getId();

        Category lighting = categoryRepository.save(Category.builder().name("Lighting").build());
        lightingId = lighting.getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---- findByCategoryId ----

    @Test
    void findByCategoryId_returnsSubcategoriesOrderedByName() {
        subcategoryService.create(furnitureId, new SubcategoryRequest("Tables"));
        subcategoryService.create(furnitureId, new SubcategoryRequest("Chairs"));
        subcategoryService.create(furnitureId, new SubcategoryRequest("Sofas"));

        List<SubcategoryResponse> result = subcategoryService.findByCategoryId(furnitureId);

        assertEquals(3, result.size());
        assertEquals("Chairs", result.get(0).name());
        assertEquals("Sofas", result.get(1).name());
        assertEquals("Tables", result.get(2).name());
    }

    @Test
    void findByCategoryId_returnsOnlySubcategoriesForRequestedCategory() {
        subcategoryService.create(furnitureId, new SubcategoryRequest("Chairs"));
        subcategoryService.create(lightingId, new SubcategoryRequest("Floor Lamps"));

        List<SubcategoryResponse> furniture = subcategoryService.findByCategoryId(furnitureId);
        List<SubcategoryResponse> lighting = subcategoryService.findByCategoryId(lightingId);

        assertEquals(1, furniture.size());
        assertEquals("Chairs", furniture.get(0).name());
        assertEquals(1, lighting.size());
        assertEquals("Floor Lamps", lighting.get(0).name());
    }

    @Test
    void findByCategoryId_missingCategory_throwsResourceNotFoundException() {
        UUID nonexistent = UUID.randomUUID();

        assertThrows(ResourceNotFoundException.class,
                () -> subcategoryService.findByCategoryId(nonexistent));
    }

    @Test
    void findByCategoryId_emptyCategory_returnsEmptyList() {
        List<SubcategoryResponse> result = subcategoryService.findByCategoryId(furnitureId);

        assertTrue(result.isEmpty());
    }

    // ---- create ----

    @Test
    void create_persistsSubcategoryAndReturnsMappedResponse() {
        SubcategoryResponse result = subcategoryService.create(furnitureId, new SubcategoryRequest("Chairs"));

        assertNotNull(result.id());
        assertEquals("Chairs", result.name());
        assertEquals(furnitureId, result.categoryId());
        assertEquals("Furniture", result.categoryName());
        assertNotNull(result.createdAt());

        assertTrue(subcategoryRepository.existsById(result.id()));
    }

    @Test
    void create_duplicateNameInSameCategory_throwsDuplicateResourceException() {
        subcategoryService.create(furnitureId, new SubcategoryRequest("Chairs"));

        assertThrows(DuplicateResourceException.class,
                () -> subcategoryService.create(furnitureId, new SubcategoryRequest("Chairs")));
    }

    @Test
    void create_sameNameInDifferentCategories_succeeds() {
        SubcategoryResponse inFurniture = subcategoryService.create(furnitureId, new SubcategoryRequest("Modern"));
        SubcategoryResponse inLighting = subcategoryService.create(lightingId, new SubcategoryRequest("Modern"));

        assertNotEquals(inFurniture.id(), inLighting.id());
        assertEquals(furnitureId, inFurniture.categoryId());
        assertEquals(lightingId, inLighting.categoryId());
    }

    @Test
    void create_missingCategory_throwsResourceNotFoundException() {
        UUID nonexistent = UUID.randomUUID();

        assertThrows(ResourceNotFoundException.class,
                () -> subcategoryService.create(nonexistent, new SubcategoryRequest("Chairs")));
    }

    // ---- update ----

    @Test
    void update_changesNameAndPersists() {
        SubcategoryResponse created = subcategoryService.create(furnitureId, new SubcategoryRequest("Old"));

        SubcategoryResponse updated = subcategoryService.update(furnitureId, created.id(), new SubcategoryRequest("New"));

        assertEquals("New", updated.name());
        assertEquals(created.id(), updated.id());

        Subcategory reloaded = subcategoryRepository.findById(created.id()).orElseThrow();
        assertEquals("New", reloaded.getName());
    }

    @Test
    void update_unchangedName_succeedsWithoutDuplicateError() {
        SubcategoryResponse created = subcategoryService.create(furnitureId, new SubcategoryRequest("Chairs"));

        SubcategoryResponse updated = subcategoryService.update(furnitureId, created.id(), new SubcategoryRequest("Chairs"));

        assertEquals("Chairs", updated.name());
    }

    @Test
    void update_toDuplicateNameInSameCategory_throwsDuplicateResourceException() {
        subcategoryService.create(furnitureId, new SubcategoryRequest("Chairs"));
        SubcategoryResponse tables = subcategoryService.create(furnitureId, new SubcategoryRequest("Tables"));

        assertThrows(DuplicateResourceException.class,
                () -> subcategoryService.update(furnitureId, tables.id(), new SubcategoryRequest("Chairs")));
    }

    @Test
    void update_subcategoryNotFound_throwsResourceNotFoundException() {
        UUID nonexistent = UUID.randomUUID();

        assertThrows(ResourceNotFoundException.class,
                () -> subcategoryService.update(furnitureId, nonexistent, new SubcategoryRequest("Chairs")));
    }

    @Test
    void update_subcategoryBelongsToDifferentCategory_throwsResourceNotFoundException() {
        SubcategoryResponse created = subcategoryService.create(lightingId, new SubcategoryRequest("Floor Lamps"));

        assertThrows(ResourceNotFoundException.class,
                () -> subcategoryService.update(furnitureId, created.id(), new SubcategoryRequest("Renamed")));
    }

    // ---- delete ----

    @Test
    void delete_removesSubcategoryFromDatabase() {
        SubcategoryResponse created = subcategoryService.create(furnitureId, new SubcategoryRequest("Chairs"));

        subcategoryService.delete(furnitureId, created.id());

        assertFalse(subcategoryRepository.existsById(created.id()));
    }

    @Test
    void delete_subcategoryNotFound_throwsResourceNotFoundException() {
        UUID nonexistent = UUID.randomUUID();

        assertThrows(ResourceNotFoundException.class,
                () -> subcategoryService.delete(furnitureId, nonexistent));
    }

    @Test
    void delete_subcategoryBelongsToDifferentCategory_throwsResourceNotFoundException() {
        SubcategoryResponse created = subcategoryService.create(lightingId, new SubcategoryRequest("Floor Lamps"));

        assertThrows(ResourceNotFoundException.class,
                () -> subcategoryService.delete(furnitureId, created.id()));

        assertTrue(subcategoryRepository.existsById(created.id()));
    }

    @Test
    void delete_whenAssetsExist_throwsResourceInUseException() {
        SubcategoryResponse created = subcategoryService.create(furnitureId, new SubcategoryRequest("Chairs"));
        Subcategory sub = subcategoryRepository.findById(created.id()).orElseThrow();

        Asset asset = Asset.builder()
                .code("CHAIR-01")
                .title("Office Chair")
                .subcategory(sub)
                .status(AssetStatus.DRAFT)
                .version(1)
                .hasAnimation(false)
                .build();
        assetRepository.save(asset);

        assertThrows(ResourceInUseException.class,
                () -> subcategoryService.delete(furnitureId, created.id()));

        assertTrue(subcategoryRepository.existsById(created.id()));
    }
}