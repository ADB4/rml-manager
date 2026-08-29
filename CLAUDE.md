# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository overview

Management application for 3D game assets — geometry files, texture maps, LODs, variants, and mesh parts — backed by S3-compatible object storage. Work is tracked on the Jira board linked in `README.md` (project key `KAN`).

- `backend/` — Spring Boot 4.1.0, Java 25 toolchain, Gradle. The active module; almost all work happens here.
- `frontend/` — React 19 + TypeScript + Vite scaffold. Minimal so far (no test runner configured yet).
- `schema.dbml` — reference diagram of the target database schema.

Do not upgrade, downgrade, or "modernize" dependencies, the Java toolchain, or Gradle. The versions are intentionally current (Boot 4.x, Framework 7, Java 25) and may look ahead of your expectations; treat them as fixed.

## Commands

Backend (run from `backend/`):

```sh
./gradlew build                  # full build including tests
./gradlew test                   # tests only (Docker must be running — Testcontainers)
./gradlew test --tests "com.adb4.rmlmanager.controller.AssetControllerTest"                # one class
./gradlew test --tests "com.adb4.rmlmanager.controller.AssetControllerTest.create_returns201WithLocationHeader"  # one method
./gradlew bootRun                # starts Postgres/MinIO via compose.yaml automatically
./gradlew asciidoctor            # assemble REST Docs snippets (build/generated-snippets) into HTML
```

The admin seed user is `admin`; set `app.admin.password` (or `APP_ADMIN_PASSWORD`) or a random password is generated and logged at WARN on startup. MinIO console: http://localhost:9001 (`minioadmin`/`minioadmin`).

Frontend (run from `frontend/`): `npm run dev`, `npm run build`, `npm run lint`.

## Coding style

`CODING_STYLE.md` at the repo root is the style reference — read it before writing code. For backend work the relevant parts are Core Principles, Naming, and the "Carrying Over to Java / Spring Boot" section. Where its ideals conflict with established patterns in this repo (e.g. it prescribes `/api/v1/` route prefixes and `timestamp`/`url` response metadata, but existing controllers use unversioned `/api/...` routes and plain DTOs), match the existing code; when a choice is ambiguous, copy the nearest existing class rather than introducing a new pattern.

Hard conventions already established in the codebase:

- Constructor injection only — no field injection, no `@Autowired` on fields.
- DTOs are Java records (`dto/request/`, `dto/response/`). Mappers are MapStruct interfaces with `componentModel = "spring"`.
- Services are `@Service @Transactional(readOnly = true)` at class level, `@Transactional` on mutating methods.
- Errors surface through typed exceptions (`exception/`) handled centrally in `GlobalExceptionHandler` as RFC 7807 `ProblemDetail` — never `IllegalArgumentException` or ad hoc `ResponseEntity` status codes in the service layer.
- Prose comments and Javadoc; no emoji.

## Backend architecture

Layering is strict: thin `@RestController` → service (all orchestration and validation) → Spring Data JPA repositories. Entities are never returned from controllers; services return response records via MapStruct mappers.

**Domain model.** `Category` → `Subcategory` → `Asset` (unique `code`). An asset has `Lod`s (levels of detail) whose `Geometry` files live in S3; named `MeshPart`s; and `Variant`s with `TextureSet`s grouping `TextureMap`s. Assets are draft/published (`AssetStatus`) with per-user viewer/editor rows in `AssetPermission`, enforced by `security/AssetAuthorizationService` plus `@EnableMethodSecurity`.

**Auditing (cross-cutting, easy to trip over).** Entities extend `Auditable` (`createdAt`/`updatedAt`). `@CreatedBy` columns (`Asset.createdBy`, `Geometry.uploadedBy`) are non-null and filled by `JpaAuditingConfig.auditorAware()` from the authenticated `AppUserPrincipal`'s UUID — an insert without an authenticated principal fails at flush. In tests, save fixtures with a populated `SecurityContextHolder`. Hibernate Envers audits `Asset`, `Variant`, and `MeshPart`, recording the acting user through `UserRevisionListener`.

**Security.** Stateless HTTP Basic; every endpoint requires authentication except `/actuator/health` and `/actuator/info`. CSRF is disabled. `AppUserDetailsService` loads `AppUser` rows into `AppUserPrincipal` (which carries the user's UUID).

**Storage.** `service/StorageService` is the abstraction (streaming `put`, presigned GETs, `exists`, `delete`, `bucket()`); `S3StorageService` implements it with AWS SDK v2. Object key conventions are documented on the interface. Configuration comes from `S3Properties` (`storage.s3.*`) and `UploadProperties` (`storage.upload.*`), both registered in `S3ClientConfig` — never construct SDK clients or read env vars elsewhere. S3 and Postgres cannot share a transaction; `GeometryService` shows the established consistency pattern: upload to S3 before persisting, then register a `TransactionSynchronization` that deletes the object if the transaction rolls back.

**Schema.** `spring.jpa.hibernate.ddl-auto=create-drop` — Hibernate generates the schema from the entities. Flyway is not wired: `db/migration/V1__baseline.sql` exists but is inert. Stories generally do not add migrations; leave `ddl-auto` alone.

**Framework 7 / Boot 4 gotchas** (APIs moved or renamed relative to older Spring):

- Test slice annotations live in starter-specific packages: `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` / `.AutoConfigureMockMvc`.
- `ResponseEntityExceptionHandler` already maps many framework exceptions (e.g. `MaxUploadSizeExceededException`); adding your own `@ExceptionHandler` for one of those is an ambiguous-mapping startup failure — override the protected `handle*` hook instead (see `GlobalExceptionHandler`).
- Renamed constants: `HttpStatus.CONTENT_TOO_LARGE` (not the deprecated `PAYLOAD_TOO_LARGE`), `status().isContentTooLarge()` in MockMvc.

## Testing

- `TestcontainersConfiguration` (shared Postgres container via `@ServiceConnection`) is `@Import`ed by integration tests; MinIO runs as a `GenericContainer` with `storage.s3.endpoint` overridden through `@DynamicPropertySource` (see `S3StorageServiceIntegrationTest`, `GeometryUploadIntegrationTest`).
- Controller slice tests use `@WebMvcTest` + `@Import(SecurityConfig.class)` so the real filter chain runs, authenticate with `SecurityMockMvcRequestPostProcessors.user(new AppUserPrincipal(...))`, and document endpoints with Spring REST Docs (`@AutoConfigureRestDocs`, `andDo(document(...))`) — follow `AssetControllerTest`.
- MockMvc bypasses real multipart parsing, so servlet-level limits (`spring.servlet.multipart.*`) only trigger over live HTTP (`webEnvironment = RANDOM_PORT`).
