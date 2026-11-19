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

## Tests

```bash
cd backend
mvn verify
```

- `HealthControllerTest` — HTTP status and JSON shape (no database)
- `HealthIntegrationTest` — boots the app against a throwaway `pgvector/pgvector:pg17`
  container (Testcontainers; Docker must be running), checks the Flyway migration and the
  live endpoint

## Layout

```
backend/src/main/java/com/nexusai/
  NexusAiApplication.java
  platform/health/          # cross-cutting health check
backend/src/main/resources/
  application.yml
  db/migration/             # Flyway, versioned and forward-only
```

Business modules (`auth`, `user`, `workspace`, `document`, …) are added as packages under
`com.nexusai` as each is implemented, per
[module-architecture.md](docs/architecture/module-architecture.md).
