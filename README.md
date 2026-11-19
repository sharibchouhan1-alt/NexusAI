# NexusAI

AI Knowledge & Research Agent Platform. The approved architecture lives in
[docs/architecture/](docs/architecture/).

## Current status — Phase 1

- Spring Boot 4.1 / Java 21 backend (modular monolith) in [backend/](backend/)
- PostgreSQL 17 + pgvector via Docker Compose
- Flyway migrations (`V1` enables the `vector` extension)
- `GET /api/health` — reports app status, database connectivity and pgvector version

Not yet implemented: frontend, authentication, ingestion, RAG.

## Prerequisites

Java 21, Maven 3.9+, Docker with Compose.

## Run locally

```bash
cp .env.example .env            # then set DB_PASSWORD
docker compose up -d --wait     # starts postgres + pgvector
cd backend
mvn spring-boot:run             # Flyway migrates on startup
curl localhost:8080/api/health
```

The backend reads its configuration from environment variables. A `.env` file in the repo
root (or in `backend/`) is imported automatically; real environment variables take
precedence.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port (also the published Compose port) |
| `DB_NAME` | `nexusai` | Database name |
| `DB_USER` | `nexusai` | Database user |
| `DB_PASSWORD` | — (required by Compose) | Database password |
| `SERVER_PORT` | `8080` | HTTP port |

Example response:

```json
{"status":"UP","service":"nexusai-backend","timestamp":"…","database":{"status":"UP","pgvector":"0.8.7"}}
```

Returns `503` with `"status":"DOWN"` if the database is unreachable or pgvector is missing.

## API (Phase 2)

| Method | Path | Success |
| --- | --- | --- |
| `POST` | `/api/workspaces` | 201 + `Location` |
| `GET` | `/api/workspaces?page=0&size=20` | 200 (paged) |
| `GET` / `PUT` / `DELETE` | `/api/workspaces/{workspaceId}` | 200 / 200 / 204 |
| `POST` | `/api/workspaces/{workspaceId}/documents` | 201 + `Location` |
| `GET` | `/api/workspaces/{workspaceId}/documents?page=0&size=20` | 200 (paged, newest first) |
| `GET` / `PUT` / `DELETE` | `/api/workspaces/{workspaceId}/documents/{documentId}` | 200 / 200 / 204 |

Workspace body: `{"name": "...", "description": "..."}`. Document body:
`{"title": "...", "sourceType": "FILE|URL", "mediaType": "application/pdf", "origin": "..."}`
(only `title` is editable). Documents hold metadata only: no file upload or ingestion yet.

Errors are RFC 9457 problem details (`application/problem+json`): 400 for validation
(with an `errors` array of `{field, message}`), 404 for missing resources, 409 when deleting
a workspace that still has documents. A document addressed through the wrong workspace
returns the same 404 as a missing one.

## Tests

```bash
cd backend
mvn verify
```

- `HealthControllerTest` — HTTP status and JSON shape (no database)
- `HealthIntegrationTest` — boots the app against a throwaway `pgvector/pgvector:pg17`
  container (Testcontainers; Docker must be running), checks the Flyway migration and the
  live endpoint
- `WorkspaceApiIntegrationTest`, `DocumentApiIntegrationTest`: CRUD, validation, 404s,
  409 on non-empty workspace delete, and workspace isolation

## Layout

```
backend/src/main/java/com/nexusai/
  NexusAiApplication.java
  platform/health/          # cross-cutting health check
  platform/web/             # problem-details error handling, paging DTO
  workspace/                # Workspace module (entity/repository package-private)
  document/                 # Document metadata module; uses WorkspaceService only
backend/src/main/resources/
  application.yml
  db/migration/             # Flyway, versioned and forward-only
```

Business modules (`auth`, `user`, `workspace`, `document`, …) are added as packages under
`com.nexusai` as each is implemented, per
[module-architecture.md](docs/architecture/module-architecture.md).
