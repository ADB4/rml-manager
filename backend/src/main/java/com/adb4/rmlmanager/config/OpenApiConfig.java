package com.adb4.rmlmanager.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// OpenAPI / Swagger UI definition. The springdoc starter serves the interactive
// UI at /swagger-ui.html and the spec at /v3/api-docs; both sit behind the global
// HTTP Basic requirement in SecurityConfig (no permitAll entry), so a browser is
// challenged for the admin credentials before either loads. Declaring the shared
// "basicAuth" scheme here surfaces the Authorize button in the UI so "Try it out"
// requests carry credentials.
@Configuration
public class OpenApiConfig {

    private static final String BASIC_AUTH_SCHEME = "basicAuth";

    @Bean
    public OpenAPI rmlManagerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RML Manager API")
                        .description("Management API for 3D game assets — geometry, texture maps, "
                                + "LODs, variants, and mesh parts backed by S3-compatible storage.")
                        .version("0.0.1-SNAPSHOT"))
                .components(new Components()
                        .addSecuritySchemes(BASIC_AUTH_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("HTTP Basic authentication using an AppUser's credentials.")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH_SCHEME));
    }
}
