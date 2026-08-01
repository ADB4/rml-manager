package com.adb4.rmlmanager.security;

import com.adb4.rmlmanager.entity.AppUser;
import com.adb4.rmlmanager.enums.UserRole;
import com.adb4.rmlmanager.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Closes the untested half of AC 3: the service is usable from
 * {@code @PreAuthorize} SpEL expressions.
 *
 * <p>Calling the methods from a service is already covered by the unit tests.
 * SpEL is different — the bean name, method name, arity and argument types are
 * all resolved at runtime from a string, so a rename or signature change fails
 * on a live request rather than at compile time. This test evaluates the real
 * expressions against the real class.
 *
 * <p>{@link AssetAuthorizationService} is mocked rather than real: the point is
 * to prove the expression resolves and its boolean result gates the call, not
 * to re-test the rules. Because Mockito mocks the actual class, an expression
 * referring to a method that does not exist still fails here.
 *
 * <p>Deliberately avoids {@code @SpringBootTest} — no database is needed, and a
 * plain {@code @ContextConfiguration} keeps this fast enough to stay in the
 * unit test loop.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AssetAuthorizationMethodSecurityTest.TestConfig.class)
class AssetAuthorizationMethodSecurityTest {

    private static final UUID ASSET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private AssetAuthorizationService assetAuthorizationService;

    @Autowired
    private GuardedOperations guardedOperations;

    @BeforeEach
    void authenticate() {
        Mockito.reset(assetAuthorizationService);

        AppUserPrincipal principal = new AppUserPrincipal(AppUser.builder()
                .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .username("tester")
                .password("{noop}secret")
                .role(UserRole.USER)
                .build());

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, principal.getPassword(), principal.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void canViewExpressionResolvesAndAllowsWhenTrue() {
        when(assetAuthorizationService.canView(any(Authentication.class), eq(ASSET_ID))).thenReturn(true);

        assertEquals("viewed", guardedOperations.view(ASSET_ID));
    }

    @Test
    void canViewExpressionDeniesWhenFalse() {
        when(assetAuthorizationService.canView(any(Authentication.class), eq(ASSET_ID))).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> guardedOperations.view(ASSET_ID));
    }

    @Test
    void canEditExpressionResolvesAndAllowsWhenTrue() {
        when(assetAuthorizationService.canEdit(any(Authentication.class), eq(ASSET_ID))).thenReturn(true);

        assertEquals("edited", guardedOperations.edit(ASSET_ID));
    }

    @Test
    void canEditExpressionDeniesWhenFalse() {
        when(assetAuthorizationService.canEdit(any(Authentication.class), eq(ASSET_ID))).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> guardedOperations.edit(ASSET_ID));
    }

    /**
     * Characterization test — settles whether a domain exception thrown inside a
     * SpEL expression survives evaluation intact, which decides whether the
     * {@code Authentication} overloads could throw {@code ResourceNotFoundException}
     * for a missing asset (404) instead of returning false (403). KAN-27 will
     * build its error contract on the answer.
     *
     * <p>The assertion below encodes the expected behaviour: Spring's SpEL
     * {@code MethodReference} unwraps {@code InvocationTargetException} and
     * rethrows a {@code RuntimeException} cause as-is. <strong>This has not been
     * run.</strong> If it fails, the reported actual type is the real answer —
     * change the assertion to match, and record the finding on KAN-26 before
     * KAN-27 assumes either behaviour.
     */
    @Test
    void domainExceptionFromExpressionPropagatesUnwrapped() {
        when(assetAuthorizationService.canView(any(Authentication.class), eq(ASSET_ID)))
                .thenThrow(new ResourceNotFoundException("Asset", "id", ASSET_ID));

        assertThrows(ResourceNotFoundException.class, () -> guardedOperations.view(ASSET_ID));
    }

    // ---- test context ----

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {

        /**
         * Bean name must stay {@code assetAuthorizationService} — it is the name
         * the SpEL expressions below reference.
         */
        @Bean
        AssetAuthorizationService assetAuthorizationService() {
            return Mockito.mock(AssetAuthorizationService.class);
        }

        @Bean
        GuardedOperations guardedOperations() {
            return new GuardedOperations();
        }
    }

    /**
     * Stand-in for the call sites KAN-27 will annotate. The expressions here
     * should be kept identical to the ones used in production code.
     */
    static class GuardedOperations {

        @PreAuthorize("@assetAuthorizationService.canView(authentication, #assetId)")
        public String view(UUID assetId) {
            return "viewed";
        }

        @PreAuthorize("@assetAuthorizationService.canEdit(authentication, #assetId)")
        public String edit(UUID assetId) {
            return "edited";
        }
    }
}