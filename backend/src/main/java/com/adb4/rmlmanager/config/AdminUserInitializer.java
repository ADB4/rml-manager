package com.adb4.rmlmanager.config;

import com.adb4.rmlmanager.entity.AppUser;
import com.adb4.rmlmanager.enums.UserRole;
import com.adb4.rmlmanager.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
@EnableConfigurationProperties(AdminProperties.class)
public class AdminUserInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);
    private static final int GENERATED_PASSWORD_BYTES = 32;

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    public AdminUserInitializer(AppUserRepository appUserRepository,
                                PasswordEncoder passwordEncoder,
                                AdminProperties adminProperties) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminProperties = adminProperties;
    }

    @Override
    public void run(String... args) {
        String username = adminProperties.getUsername();

        if (appUserRepository.findByUsername(username).isPresent()) {
            log.debug("Admin user '{}' already exists — skipping seed", username);
            return;
        }

        String rawPassword = resolvePassword();

        AppUser admin = AppUser.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .role(UserRole.ADMIN)
                .build();
        appUserRepository.save(admin);

        log.info("Seeded admin user '{}'", username);
    }

    private String resolvePassword() {
        String configured = adminProperties.getPassword();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }

        String generated = generateRandomPassword();
        log.warn("No admin password configured — generated password: {}", generated);
        return generated;
    }

    private String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[GENERATED_PASSWORD_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}