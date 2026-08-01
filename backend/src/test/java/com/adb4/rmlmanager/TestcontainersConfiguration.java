package com.adb4.rmlmanager;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Postgres container for integration tests.
 *
 * <p>Widened from package-private to public so integration tests outside
 * {@code com.adb4.rmlmanager} can {@code @Import} it — the alternative was a
 * duplicate container definition in every package that needs a database.
 * Only the class modifier changed; the container wiring is untouched.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
    }

}