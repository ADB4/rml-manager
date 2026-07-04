# rml-manager
[JIRA board](https://buiand.atlassian.net/jira/software/projects/KAN/boards/2?jql=labels+%3D+sprint-1&atlOrigin=eyJpIjoiZTNiYjYxOGQ1MzllNDNlZjhkNDAzYzI1N2UzMmRiMTYiLCJwIjoiaiJ9)

management application for 3D assets: geometry files, texture maps, LODs, variants, and mesh parts, with S3-compatible object storage.

Built on Spring Boot 4.1.0, Java 25, PostgreSQL, and MinIO.

## Prerequisites

- JDK 25
- Docker and Docker Compose (for PostgreSQL and MinIO in development)


## Getting started

The project includes a `compose.yaml`. It provisions three containers:

- **postgres**: database
- **minio**: S3 object storage on ports 9000 (API) and 9001 (console)
- **minio-init**: container that creates the `rml-assets` bucket

Start the application:

```sh
./gradlew bootRun
```

Spring Boot will start the Compose services before the application context loads. No manual `docker compose up` is required.


## Configuration

### Admin seed user

On first startup, the application creates an initial admin account. Credentials are driven by configuration properties under the `app.admin` prefix:

| Property             | Env variable           | Default   | Description                                                                 |
|----------------------|------------------------|-----------|-----------------------------------------------------------------------------|
| `app.admin.username` | `APP_ADMIN_USERNAME`   | `admin`   | Username for the seed account.                                              |
| `app.admin.password` | `APP_ADMIN_PASSWORD`   | *(none)*  | Password for the seed account. If omitted, a random password is generated and logged at `WARN` level on startup. |

The seed is idempotent. If a user with the configured username already exists, the initializer does nothing.

For local development, you can set the password in a profile-specific properties file that is not committed:

```properties
# application-local.properties (gitignored)
app.admin.password=changeme
```

```sh
./gradlew bootRun --args='--spring.profiles.active=local'
```

In deployed environments, pass the password through the environment variable or a secrets manager bound to `APP_ADMIN_PASSWORD`.

### S3 / MinIO

Object storage is configured under `storage.s3`:

| Property                             | Default                  | Description                              |
|--------------------------------------|--------------------------|------------------------------------------|
| `storage.s3.region`                  | `us-east-1`              | AWS region.                              |
| `storage.s3.endpoint`                | `http://localhost:9000`  | Endpoint override for MinIO or other S3-compatible stores. Remove for production AWS S3. |
| `storage.s3.path-style-access`       | `true`                   | Required for MinIO. Set to `false` for AWS S3. |
| `storage.s3.bucket`                  | `rml-assets`             | Bucket name.                             |
| `storage.s3.presigned-url-expiration`| `PT1H`                   | Duration for presigned GET URLs.         |
| `storage.s3.access-key`              | `minioadmin`             | Static access key (local dev only).      |
| `storage.s3.secret-key`              | `minioadmin`             | Static secret key (local dev only).      |

When `access-key` and `secret-key` are both blank or absent, the SDK falls back to the default AWS credentials provider chain.

### Database

`application.properties` comes with `spring.jpa.hibernate.ddl-auto=create-drop` for local development. A Flyway-style baseline migration exists at `src/main/resources/db/migration/V1__baseline.sql` for environments that use managed schema migrations instead.


## Project structure

```
src/main/java/com/adb4/rmlmanager/
├── config/             # JPA auditing, S3 client, admin seed initializer
├── controller/         # REST controllers
├── dto/
│   ├── request/        # Inbound request records
│   └── response/       # Outbound response records
├── entity/             # JPA entities (Asset, Lod, Geometry, Variant, TextureSet, …)
├── enums/              # AssetStatus, UserRole, GeometryFileType, TextureMapType, …
├── exception/          # Problem Detail exception handling
├── mapper/             # MapStruct mappers
├── repository/         # Spring Data JPA repositories
├── security/           # UserDetails, UserDetailsService, SecurityFilterChain
└── service/            # Business logic, S3 storage abstraction
```

### Domain Vocabulary

An **Asset** belongs to a **Subcategory** (which belongs to a **Category**) and is identified by a unique `code`. Each asset can have:

- **LODs** (levels of detail), each containing one or more **Geometry** files stored in S3.
- **Mesh parts**, named sub-objects of the asset (e.g. legs, seat, back).
- **Variants**, color or material alternatives, each with a set of **Texture sets** that group **Texture maps** (albedo, normal, roughness, etc.).

Assets support draft/published status and per-user **permissions** (viewer or editor).

Hibernate Envers tracks revision history on `Asset`, `Variant`, and `MeshPart` entities, with the acting user recorded in each revision via `UserRevisionListener`.


## Authentication

The API uses HTTP Basic authentication over a stateless session. Every endpoint except `/actuator/health` and `/actuator/info` requires authentication.

Method-level security is enabled via `@EnableMethodSecurity`.


## Running tests

```sh
./gradlew test
```

Tests use Testcontainers, so Docker must be running. The test suite includes:

- `AssetControllerTest` — WebMvc slice tests for REST endpoints and Problem Detail error responses.
- `S3StorageServiceIntegrationTest` — round-trip tests against a MinIO container for put, get, delete, and presigned URL generation.


## Building

```sh
./gradlew build
```

The resulting JAR is written to `build/libs/`.


## API documentation

The project is configured with Spring REST Docs and Asciidoctor. Generated snippets are written to `build/generated-snippets/` during the test phase, and the Asciidoctor task assembles them into HTML documentation.