# Document Management Service

Spring Boot 3 / Java 17 REST service that stores PDF metadata in PostgreSQL and document bytes in MinIO (S3-compatible). Built for the Clara Ops technical challenge.

- **Upload** (presigned flow: `POST /upload` → client PUTs bytes to MinIO → `POST /upload/{id}/complete`)
- **Search** with filters + pagination (`POST /search`)
- **Download** via short-lived presigned URLs (`GET /download/{id}`)
- Runs under a **50 MB memory cap** while handling 500 MB files × 10 concurrent uploads — because the service never buffers payloads in the JVM.

---

## Table of Contents

1. [Architecture at a glance](#architecture-at-a-glance)
2. [Prerequisites](#prerequisites)
3. [Quick start with Docker Compose](#quick-start-with-docker-compose)
4. [Configuration reference](#configuration-reference)
5. [API usage](#api-usage)
6. [Running tests & coverage locally](#running-tests--coverage-locally)
7. [CI / code quality reports](#ci--code-quality-reports)
8. [Assumptions & deviations from the spec](#assumptions--deviations-from-the-spec)
9. [Troubleshooting](#troubleshooting)

---

## Architecture at a glance

```
              ┌────────────────────┐
JSON metadata │  document-management-service  │      bytes
──────────────▶ Spring Boot 3 (50 MB heap) ◀──────────┐
              └────────┬───────────┘                  │
                       │ JDBC                         │ S3 API
                       ▼                              ▼
                ┌────────────┐                ┌────────────┐
                │ PostgreSQL │                │   MinIO    │
                │ (metadata) │                │ (objects)  │
                └────────────┘                └────────────┘
```

Layered architecture: `api` (controllers, DTOs, advice) → `domain.service` → `domain.repo` (JPA) + `storage` (MinIO adapter behind a `StoragePort` interface). Specifications build composable, optional filters for search.

See [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md) (local-only, not tracked) for milestone-by-milestone notes.

---

## Prerequisites

- **Docker** ≥ 20.10 / Docker Desktop ≥ 4.20 (must expose API ≥ 1.40)
- **Java 17** (only if you want to run the build outside Docker)
- **Maven wrapper** (`./mvnw`) — no local Maven install required

---

## Quick start with Docker Compose

```bash
# 1. copy env template and edit secrets (or use the defaults; they are local-only)
cd docker
cp .env.example .env

# 2. build & start the full stack
docker compose up --build

# service    → http://localhost:8080
# MinIO API  → http://localhost:9000
# MinIO UI   → http://localhost:9001   (login with MINIO_ROOT_USER / MINIO_ROOT_PASSWORD from .env)
# Postgres   → localhost:5432
```

The compose stack starts, in order:

1. **postgresql** — bitnami/postgresql:15, creates `document_schema` via `init-scripts/schema-init.sql`.
2. **minio** — MinIO server with the console on `:9001`.
3. **minio-bootstrap** — one-shot `mc` sidecar that creates the `document-bucket` and the service access key.
4. **document-management-service** — built from the root `Dockerfile`, hard-capped to 50 MB of RAM, `JAVA_OPTS=-Xmx40m -Xms40m -Xss256k -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=32m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError`.

Shut it all down with `docker compose down -v` (the `-v` also removes the named volumes so next boot is clean).

---

## Configuration reference

All settings are injected through environment variables (see `docker/.env.example`). The Spring binding lives in `src/main/resources/application.yml`.

|            Variable            |      Default      |                      Description                      |
|--------------------------------|-------------------|-------------------------------------------------------|
| `POSTGRESQL_USERNAME`          | —                 | App DB user                                           |
| `POSTGRESQL_PASSWORD`          | —                 | App DB password                                       |
| `POSTGRESQL_POSTGRES_PASSWORD` | —                 | `postgres` root password (Bitnami image needs this)   |
| `POSTGRESQL_DATABASE`          | `challenge`       | DB name                                               |
| `MINIO_ROOT_USER`              | —                 | MinIO root user (console login)                       |
| `MINIO_ROOT_PASSWORD`          | —                 | MinIO root password                                   |
| `MINIO_ACCESS_KEY`             | —                 | Service access key (created by the bootstrap sidecar) |
| `MINIO_SECRET_KEY`             | —                 | Service secret key                                    |
| `MINIO_BUCKET`                 | `document-bucket` | Bucket name                                           |
| `APP_PORT`                     | `8080`            | Host port the service listens on                      |
| `MINIO_PUT_TTL_MIN`            | `15`              | Presigned PUT URL TTL, minutes                        |
| `MINIO_GET_TTL_MIN`            | `5`               | Presigned GET URL TTL, minutes                        |

`.env` and `docker/.env` are git-ignored — secrets never land in the repo.

---

## API usage

Base path: `/document-management` · Content type: `application/json` unless noted.

### 1. Initiate an upload

```bash
curl -sS -X POST http://localhost:8080/document-management/upload \
  -H "Content-Type: application/json" \
  -d '{
    "user": "alice",
    "name": "quarterly-report.pdf",
    "tags": ["finance", "2026Q1"]
  }'
```

```json
{
  "id": "b0b6c7e0-...-...",
  "uploadUrl": "http://minio:9000/document-bucket/alice/quarterly-report.pdf?X-Amz-...",
  "expiresAt": "2026-04-19T22:30:00Z"
}
```

### 2. PUT the bytes directly to MinIO

```bash
curl -sS -X PUT --data-binary @/path/to/report.pdf \
  -H "Content-Type: application/pdf" \
  "<uploadUrl from step 1>"
```

### 3. Mark the upload complete

```bash
curl -sS -X POST http://localhost:8080/document-management/upload/<id>/complete
```

```json
{ "id": "b0b6c7e0-...", "status": "AVAILABLE", "sizeBytes": 1048576, "contentType": "application/pdf" }
```

`409 Conflict` is returned if the PUT in step 2 never happened (`UploadNotReadyException`).

### 4. Search

```bash
curl -sS -X POST "http://localhost:8080/document-management/search?page=0&size=20" \
  -H "Content-Type: application/json" \
  -d '{ "user": "alice", "tags": ["finance"] }'
```

Filters are all optional — sending `{}` returns every AVAILABLE document. Default sort is `createdAt,desc`; override with the `sort` query parameter (`?sort=name,asc`).

### 5. Download

```bash
curl -sS http://localhost:8080/document-management/download/<id>
# { "url": "http://minio:9000/...?X-Amz-..." }

# then
curl -sS -o report.pdf "<url>"
```

404 is returned both when the id is unknown and when the document is still `PENDING` — we don't leak the existence of unfinished uploads.

### Error envelope

Every error flows through `GlobalExceptionHandler` and returns a consistent shape:

```json
{
  "timestamp": "2026-04-19T22:35:12Z",
  "status": 404,
  "error": "Not Found",
  "message": "Document not found: b0b6c7e0-...",
  "path": "/document-management/download/b0b6c7e0-...",
  "violations": null
}
```

Every response also carries `X-Request-Id` (echoed back if supplied, otherwise generated) and the same id appears in the server logs in the `[requestId]` slot.

---

## Running tests & coverage locally

```bash
# unit + slice tests (fast, ~2s, no Docker required)
./mvnw -B test -Dtest='DocumentServiceTest,DocumentControllerTest'

# full verify: above + the Testcontainers integration test + Jacoco + SpotBugs + Spotless check
./mvnw -B verify
```

Artifacts produced:

- `target/site/jacoco/index.html` — Jacoco HTML coverage report
- `target/spotbugs.html` — SpotBugs findings
- `target/surefire-reports/*` — per-test XML + text
- `target/document-management-service-challenge-*.jar` — Spring Boot fat jar

The integration test (`DocumentManagementIntegrationTest`) boots Postgres + MinIO via Testcontainers and exercises the full flow (init → PUT → complete → search → download → GET byte comparison). It requires a working Docker daemon — see [Troubleshooting](#troubleshooting) if Testcontainers can't find one locally. CI runs it every push.

---

## CI / code quality reports

GitHub Actions (`.github/workflows/ci.yml`) runs on every branch push and against PRs into `develop`/`main`:

1. `./mvnw spotless:check` — formatting gate.
2. `./mvnw verify` — compile, unit + slice + integration tests, Jacoco, SpotBugs.
3. Uploads artifacts: `jacoco-report`, `surefire-reports`, `spotbugs-report`, `app-jar` (each downloadable from the run page).

Inspect the latest run at:
`https://github.com/amaro-coria/document-management-service-challenge/actions`

---

## Assumptions & deviations from the spec

The challenge OpenAPI spec describes upload as `application/json` with just `{user, name, tags}` — no file field. Three reasons to interpret that literally and adopt a **presigned-URL** flow rather than streaming multipart through the service:

1. **Memory contract.** The service container is capped at **50 MB**, yet PDFs can be **500 MB × 10 concurrent uploads** = 5 GB of in-flight data. Even streamed multipart requires Tomcat buffers, TLS buffers, and a multipart parser — all competing for the 40 MB heap. Presigned PUT removes the JVM from the data path entirely: MinIO receives bytes directly.
2. **Contract fidelity.** The upload endpoint stays `application/json` with the documented body. Adding a file part would deviate further than the presigned approach does conceptually.
3. **Standard S3 idiom.** This is the same pattern AWS recommends for large object uploads.

Deviations introduced:

- **New endpoint** `POST /document-management/upload/{id}/complete` — the client calls this after the PUT succeeds. The service then `statObject`s MinIO, records size + content-type, and flips `status` from `PENDING` to `AVAILABLE`. Returns 409 if the object isn't there yet.
- **`Document.size`** is returned as a JSON number large enough for a long (`int64`) rather than `int32` as the spec shows. The JPA field is `BIGINT`; narrowing to int32 would lose precision for ≥ 2 GB files. Low risk of client breakage for numeric JSON readers.
- **`status` field** added to the domain (not in the spec). Search and download hide `PENDING` rows from callers.

Other intentional decisions worth flagging:

- **Indexing strategy** (`schema-init.sql`): only `documents.user_name` and `document_tags.tag`. Indexes on `created_at`, `LOWER(name)`, and `status` were explicitly considered and dropped as unjustified at POC scale — full reasoning is inline in the SQL and in `IMPLEMENTATION_PLAN.md` (M1 notes).
- **No auth.** The challenge doesn't require it; the "user" field is treated as ambient metadata, not an authenticated principal.
- **`ddl-auto: validate`.** Hibernate validates the schema against the hand-written `schema-init.sql` rather than generating it — the SQL file is the source of truth.

---

## Troubleshooting

**Integration test fails with "Could not find a valid Docker environment"**
Your Docker daemon is unreachable to Testcontainers. Check `docker info` returns real data (not empty strings). On Docker Desktop 4.69.x specifically, enable Settings → Advanced → *"Allow the default Docker socket to be used"*, disable Enhanced Container Isolation, and fully restart Docker Desktop. Alternatively, use Colima (`colima start --runtime docker`) or rely on CI (`push` → GitHub Actions) as the authoritative runner.

**Service fails to boot with `OutOfMemoryError`**
You likely raised upload handling complexity beyond the presigned model. Verify the service never reads the PDF bytes — the controller body is JSON only.

**`./mvnw spotless:check` fails**
Run `./mvnw spotless:apply` to auto-format, then re-commit.

**Compose can't bind 5432 / 9000 / 9001**
Set `APP_PORT` / map the port through compose, or stop the conflicting local service (`lsof -i :5432`).
