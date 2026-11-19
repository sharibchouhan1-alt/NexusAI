# NexusAI

AI Knowledge & Research Agent Platform. The approved architecture lives in
[docs/architecture/](docs/architecture/).

## Current status — Phase 5

- Spring Boot 4.1 / Java 21 backend (modular monolith) in [backend/](backend/)
- PostgreSQL 17 + pgvector via Docker Compose, Flyway migrations
- Workspace and document REST APIs
- Document ingestion: PDF/TXT upload → local file storage → text extraction → chunks with
  page/offset provenance in PostgreSQL, with processing status
- Local semantic search: chunk embeddings from Ollama `nomic-embed-text` (768-d, via
  Spring AI) stored in pgvector; workspace-scoped nearest-passage search with provenance
- Next.js 16 / React 19 / Tailwind 4 frontend in [frontend/](frontend/): workspaces,
  documents, uploads with progress, ingestion/index status, chunk viewer, search

Not yet implemented: authentication/authorization, RAG / generated answers (search
returns passages only; no LLM is called).

> **Do not expose this to a network you don't control.** There is no authentication yet:
> anyone who can reach the API can read, upload and delete any workspace's documents.
> Run it on localhost only until the Auth module exists.

## Prerequisites

Java 21, Maven 3.9+, Node.js 20+, Docker with Compose, and [Ollama](https://ollama.com)
running locally with the embedding model pulled (free, no API keys):

```bash
ollama pull nomic-embed-text   # Ollama must be running on localhost:11434
```

Without Ollama everything except search keeps working: uploads are extracted and chunked,
documents wait for embeddings ("Waiting for Ollama") and are indexed automatically once
it is reachable; the search API answers 503 with `Retry-After`.

## Run locally

```bash
cp .env.example .env            # then set DB_PASSWORD
docker compose up -d --wait     # starts postgres + pgvector

# terminal 1: API on :8080 (Flyway migrates on startup)
cd backend && mvn spring-boot:run

# terminal 2: UI on :3000
cd frontend && npm install && npm run dev
```

Open http://localhost:3000. For a production build: `npm run build && npm start`.

### Frontend configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | API base URL, inlined at build time (see `frontend/.env.example`) |

The browser calls the API directly. The backend only accepts cross-origin requests from
the origins in `CORS_ALLOWED_ORIGINS` (default `http://localhost:3000`, no credentials).

Frontend checks: `npm run typecheck`, `npm run lint`, `npm run build`.

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
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated browser origins allowed to call `/api/**` |
| `STORAGE_LOCAL_ROOT` | `~/.nexusai/storage` | Where uploaded originals are stored (keep outside Git) |
| `MAX_UPLOAD_SIZE` / `MAX_UPLOAD_REQUEST_SIZE` | `25MB` / `26MB` | Upload limits (larger → 413) |
| `INGESTION_CHUNK_SIZE` / `INGESTION_CHUNK_OVERLAP` | `1200` / `200` | Max characters per chunk / overlap (overlap < half the size) |
| `INGESTION_MAX_PAGES` / `INGESTION_MAX_TEXT_CHARS` | `2000` / `5000000` | Extraction limits; larger documents fail with a reason |
| `INGESTION_WORKER_THREADS` / `INGESTION_QUEUE_CAPACITY` | `2` / `100` | In-process ingestion pool |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Local Ollama server |
| `EMBEDDING_MODEL` / `EMBEDDING_DIMENSIONS` | `nomic-embed-text` / `768` | Model and vector size (must match `vector(768)` in V6) |
| `EMBEDDING_DOCUMENT_PREFIX` / `EMBEDDING_QUERY_PREFIX` | `search_document: ` / `search_query: ` | nomic task prefixes |
| `EMBEDDING_BATCH_SIZE` | `32` | Chunks per Ollama call |
| `EMBEDDING_RETRY_INTERVAL` | `30s` | Retry cadence for documents waiting on Ollama |
| `OLLAMA_CONNECT_TIMEOUT` / `OLLAMA_READ_TIMEOUT` | `2s` / `60s` | HTTP timeouts to Ollama |
| `SEARCH_DEFAULT_LIMIT` / `SEARCH_MAX_LIMIT` | `10` / `50` | Results per query |

Example response:

```json
{"status":"UP","service":"nexusai-backend","timestamp":"…","database":{"status":"UP","pgvector":"0.8.7"}}
```

Returns `503` with `"status":"DOWN"` if the database is unreachable or pgvector is missing.

## API

| Method | Path | Success |
| --- | --- | --- |
| `POST` | `/api/workspaces` | 201 + `Location` |
| `GET` | `/api/workspaces?page=0&size=20` | 200 (paged) |
| `GET` / `PUT` / `DELETE` | `/api/workspaces/{workspaceId}` | 200 / 200 / 204 |
| `POST` (JSON) | `/api/workspaces/{workspaceId}/documents` | 201: metadata-only document |
| `POST` (multipart) | `/api/workspaces/{workspaceId}/documents` | 202: upload, ingestion starts |
| `GET` | `/api/workspaces/{workspaceId}/documents?page=0&size=20` | 200 (paged, newest first) |
| `GET` / `PUT` / `DELETE` | `/api/workspaces/{workspaceId}/documents/{documentId}` | 200 / 200 / 204 |
| `GET` | `/api/workspaces/{workspaceId}/documents/{documentId}/chunks?page=0&size=20` | 200 (paged) |
| `POST` | `/api/workspaces/{workspaceId}/documents/{documentId}/ingestion` | 202: re-run ingestion |
| `POST` | `/api/workspaces/{workspaceId}/documents/{documentId}/embeddings` | 202: re-run indexing now |
| `GET` | `/api/workspaces/{workspaceId}/search?q=...&limit=10` | 200: ranked passages; 503 if Ollama is down |

Search response: `{query, model, coverage: {indexed, pending, failed}, results: [{rank, score,
documentId, documentTitle, documentOrigin, mediaType, chunkId, chunkIndex, pageNumber,
charStart, charEnd, content}]}`. `score` is cosine similarity (higher = closer). Documents
carry `indexStatus` (`PENDING`, `INDEXING`, `INDEXED`, `FAILED`; null until READY) and
`indexDetail` (why it is waiting or failed).

Workspace body: `{"name": "...", "description": "..."}`. Metadata body:
`{"title": "...", "sourceType": "FILE|URL", "mediaType": "application/pdf", "origin": "..."}`
(only `title` is editable). Upload: multipart field `file` (`.pdf` or `.txt`) and optional
`title`, e.g. `curl -F file=@paper.pdf localhost:8080/api/workspaces/$WS/documents`.

Document `status`: `REGISTERED` (metadata only) → for uploads `PENDING` → `PROCESSING` →
`READY` (with `pageCount`, `chunkCount`) or `FAILED` (with a user-facing `statusDetail`;
retry via `POST …/ingestion`). Deleting a document removes its chunks and stored file.

Errors are RFC 9457 problem details (`application/problem+json`): 400 for validation
(with an `errors` array of `{field, message}`) or an empty file, 404 for missing resources,
409 for conflicts (non-empty workspace delete, ingestion already running), 413 for files
over the size limit, 415 for unsupported or mismatched file types. A document addressed
through the wrong workspace returns the same 404 as a missing one.

### Ingestion design

- **Module boundaries:** the Document module validates and stores the original and owns
  the lifecycle status. After commit it publishes an in-process `DocumentIngestionRequested`
  event. The Ingestion module consumes it on a bounded worker pool, reads the original
  through `DocumentService`, and owns `document_chunks`. Document never depends on
  Ingestion.
- **Upload safety:** the type comes from the extension *and* the bytes (`%PDF-` header,
  or no NUL bytes for text). The client's Content-Type is ignored. The stored file name
  is a random UUID under `<root>/<workspaceId>/`. User file names are only display
  metadata, reduced to a safe base name. Files are written to a temp file and atomically
  moved into place, with owner-only permissions (700/600).
- **Extraction:** PDFBox per page, with temp-file buffering and page/character limits.
  Text must be valid UTF-8. Control characters (including NUL) are stripped. Scanned,
  image-only, encrypted and damaged PDFs fail with a clear reason. There is no OCR.
- **Chunking:** chunks never cross a page boundary. Each is ≤ `INGESTION_CHUNK_SIZE`
  characters, split at paragraph, then line, then sentence, then word boundaries, with a
  word-aligned overlap. `charStart`/`charEnd` index the page's extracted text exactly.
- **Recovery:** each step is a short transaction. On startup, documents left
  `PENDING`/`PROCESSING` are re-queued. This assumes a single backend instance.
- **Workspace isolation:** chunks carry `workspace_id`. A composite foreign key to
  `documents (id, workspace_id)` makes it impossible to attach a chunk to another
  workspace's document.

### Embeddings and search design

- **Modules:** Ingestion owns `chunk_embeddings`, kept separate from `document_chunks` so
  a model change never re-derives chunks. Every row records its model
  (`ollama:nomic-embed-text`), and search only compares vectors of the configured model.
  The new Search module embeds the query, retrieves through Ingestion's published
  `ChunkSearch` interface, and adds titles via `DocumentService`. No module reads another
  module's tables. Spring AI types appear only in the `platform.ai.EmbeddingService` adapter.
- **Pipeline:** extract → chunk → document READY (`indexStatus = PENDING`) → embed in
  batches of chunks that still lack a vector → `INDEXED`. Each batch is its own
  transaction, so interrupted indexing resumes instead of starting over.
- **Ollama downtime:** provider errors become one retryable failure. The document goes
  back to `PENDING` with an explanation. A scheduler probes Ollama once per
  `EMBEDDING_RETRY_INTERVAL` and resumes waiting work, and a manual retry endpoint and
  button exist too. Search answers 503 with `Retry-After`.
- **Backfill:** V6 marks existing READY documents `PENDING`. On startup, any document
  with chunks lacking embeddings for the current model is queued again. This covers older
  documents and model changes.
- **Retrieval:** pgvector HNSW (cosine) index plus a workspace and model filter, with
  `hnsw.iterative_scan = relaxed_order` so filtering can't starve small workspaces.
  Results are re-sorted by exact distance.
- **Integrity:** `(chunk_id, document_id, workspace_id)` foreign-keys to the chunk, so an
  embedding can never point across documents or workspaces. Deleting or re-ingesting a
  document cascades to its embeddings.

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
- `IngestionApiIntegrationTest`: TXT/PDF upload → READY with chunks and page numbers,
  rejected uploads (nothing stored), failure reasons, retry/idempotent re-ingestion,
  workspace scoping (API and database constraint), deletion of chunks and files
- `UploadSizeLimitIntegrationTest`: 413 through a real HTTP server
- `SemanticSearchIntegrationTest`, `IndexingIntegrationTest`: ranking and provenance, PDF
  page numbers, workspace isolation, validation, 503 while Ollama is down, automatic
  resume, wrong-dimension failure and manual retry, re-ingestion, deletion, backfill, DB
  integrity. These use a deterministic test-only embedding model in place of Ollama,
  following the architecture's stubbed-provider testing posture.
- `OllamaSemanticSearchTest`: end to end with the **real** `nomic-embed-text`. The query
  shares no keywords with the expected passage. Skipped automatically if Ollama or the
  model is unavailable.
- `EmbeddingServiceTest`: prefixes, error translation, vector contract
- Unit tests: `TextChunkerTest`, `TextExtractorTest` (generated PDFs: multi-page,
  encrypted, image-only, corrupt), `UploadInspectorTest`, `LocalFileStorageTest` (path
  traversal, permissions)

Tests store uploads under `${java.io.tmpdir}/nexusai-test-storage`, never your real storage.

## Layout

```
backend/src/main/java/com/nexusai/
  NexusAiApplication.java
  platform/health/          # cross-cutting health check
  platform/web/             # problem-details errors, paging DTO, CORS
  platform/storage/         # FileStorage + local filesystem implementation
  workspace/                # Workspace module (entity/repository package-private)
  document/                 # Documents, uploads, lifecycle; uses WorkspaceService only
  ingestion/                # extraction, chunking, chunks + embeddings, indexing; uses DocumentService
  search/                   # semantic search API; uses EmbeddingService, ChunkSearch, DocumentService
  platform/ai/              # EmbeddingService: Spring AI / Ollama adapter
backend/src/main/resources/
  application.yml
  db/migration/             # Flyway, versioned and forward-only
frontend/src/
  app/                      # routes: / (dashboard), /workspaces/[workspaceId]
  lib/api/                  # typed API client mirroring the backend DTOs
  components/ui/            # buttons, fields, modal, states, pagination
  components/{layout,workspaces,documents}/
```

Business modules (`auth`, `user`, `workspace`, `document`, …) are added as packages under
`com.nexusai` as each is implemented, per
[module-architecture.md](docs/architecture/module-architecture.md).
