# NexusAI

AI Knowledge & Research Agent Platform. The approved architecture lives in
[docs/architecture/](docs/architecture/).

## Current status — Phase 7

- Spring Boot 4.1 / Java 21 backend (modular monolith) in [backend/](backend/)
- PostgreSQL 17 + pgvector via Docker Compose, Flyway migrations
- Workspace and document REST APIs
- Document ingestion: PDF/TXT upload → local file storage → text extraction → chunks with
  page/offset provenance in PostgreSQL, with processing status
- Local semantic search: chunk embeddings from Ollama `nomic-embed-text` (768-d, via
  Spring AI) stored in pgvector; workspace-scoped nearest-passage search with provenance
- Local RAG chat: workspace-scoped conversations with persisted messages; answers from a
  local Ollama model (`qwen2.5:7b`) grounded in retrieved passages, with **verified
  citations** (document, chunk, page, character range, exact quote). It abstains when the
  evidence is insufficient.
- Next.js 16 / React 19 / Tailwind 4 frontend in [frontend/](frontend/): workspaces,
  documents, uploads with progress, ingestion/index status, chunk viewer, search, chat
  with clickable sources, research runs with live plan/tool activity and cited reports
- Research agent: a workspace-scoped objective → model-made plan → one bounded tool-using
  agent loop per step (`search_knowledge`, `retrieve_source`) → a synthesized report whose
  citations are verified against the gathered passages. Runs, plans, steps, tool calls,
  evidence and reports are persisted; runs can be cancelled and resumed.

Not yet implemented: authentication/authorization, streaming answers (research progress is
polled), web search, memory.

> **Do not expose this to a network you don't control.** There is no authentication yet:
> anyone who can reach the API can read, upload and delete any workspace's documents.
> Run it on localhost only until the Auth module exists.

## Prerequisites

Java 21, Maven 3.9+, Node.js 20+, Docker with Compose, and [Ollama](https://ollama.com)
running locally with both models pulled (free, no API keys):

```bash
ollama pull nomic-embed-text   # embeddings, ~0.3 GB
ollama pull qwen2.5:7b         # chat answers, ~4.7 GB (Q4); comfortable on a 16 GB Mac
```

`qwen2.5:7b` was chosen for reliable instruction-following and JSON output at a size that
fits next to Docker, the JVM and an IDE in 16 GB. On machines with less memory use
`CHAT_MODEL=qwen2.5:3b` (~1.9 GB), though citations will be less reliable.

Without Ollama, uploads still work: files are extracted and chunked, documents wait for
embeddings ("Waiting for Ollama") and are indexed automatically once it is reachable.
Search answers 503 with `Retry-After`. Chat answers are stored as FAILED with the reason
and can be retried.

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
| `CHAT_MODEL` | `qwen2.5:7b` | Local Ollama instruct model for answers |
| `CHAT_TEMPERATURE` / `CHAT_SEED` | `0.1` / `42` | Low randomness for grounded answers |
| `CHAT_CONTEXT_TOKENS` / `CHAT_MAX_TOKENS` | `8192` / `800` | Model context window / answer length cap |
| `RAG_TOP_K` / `RAG_MIN_SCORE` | `6` / `0.5` | Passages offered as evidence / similarity threshold |
| `RAG_MAX_CONTEXT_CHARS` / `RAG_MIN_QUOTE_CHARS` | `12000` / `12` | Evidence budget / shortest verifiable quote |
| `OLLAMA_READ_TIMEOUT` | `120s` | Bounds one generation; beyond it the answer fails as retryable |
| `RESEARCH_MAX_STEPS` / `RESEARCH_MAX_ACTIONS_PER_STEP` | `4` / `4` | Plan size / agent decisions per step |
| `RESEARCH_MAX_TOOL_CALLS` / `RESEARCH_MAX_SOURCES` | `20` / `40` | Tool calls / distinct passages per run |
| `RESEARCH_MAX_DURATION` | `10m` | Wall-clock limit per execution; beyond it the run fails (resumable) |
| `RESEARCH_SYNTHESIS_MAX_SOURCES` / `RESEARCH_SYNTHESIS_MAX_CHARS` | `12` / `12000` | Evidence given to the report writer |
| `RESEARCH_MAX_RESUMES` | `2` | Automatic resumes after a restart interrupts a run |
| `RESEARCH_WORKER_THREADS` / `RESEARCH_QUEUE_CAPACITY` | `1` / `20` | In-process research pool (one run at a time fits a 16 GB machine) |

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

| `POST` / `GET` | `/api/workspaces/{workspaceId}/conversations` | 201 / 200 (paged, recent first) |
| `GET` / `PUT` / `DELETE` | `/api/workspaces/{workspaceId}/conversations/{conversationId}` | 200 (with messages + citations) / 200 / 204 |
| `POST` | `/api/workspaces/{workspaceId}/conversations/{conversationId}/messages` | 201: `{question, answer}` once answered; 409 while one is pending |
| `POST` | `/api/workspaces/{workspaceId}/conversations/{conversationId}/messages/{messageId}/retry` | 200: regenerate a FAILED answer |
| `POST` / `GET` | `/api/workspaces/{workspaceId}/research` | 202 `{objective}`: run queued / 200 (paged, recent first) |
| `GET` / `DELETE` | `/api/workspaces/{workspaceId}/research/{runId}` | 200 (plan, steps, tool calls, sources, report, citations) / 204 (409 while active) |
| `POST` | `/api/workspaces/{workspaceId}/research/{runId}/cancel` | 202: stops before the next action |
| `POST` | `/api/workspaces/{workspaceId}/research/{runId}/resume` | 202: continue a FAILED/CANCELLED run where it stopped |

An answer's `status` is `COMPLETE` (grounded, with `citations`), `ABSTAINED` (fixed
"couldn't find enough evidence" text plus a `statusDetail` reason), or `FAILED` (retryable:
model or search unavailable, timeout, malformed output). Each citation has `{ordinal,
documentId, chunkId, documentTitle, pageNumber, chunkIndex, charStart, charEnd, quote,
passage, score}`. `ordinal` matches the `[n]` marker in the answer text.

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

### RAG chat and citation design

- **Modules:** Chat (conversations, messages, turn order) → RAG (retrieval via Search's
  published `retrieve`, context assembly, generation) → Citation (verification and storage
  of evidence links). Spring AI chat types appear only in `platform.ai.GenerationService`.
- **A turn** is two short transactions around the model call. First the conversation is
  locked and the question and a `PENDING` answer are stored. Then RAG runs with no
  transaction open. Finally the outcome and its citations are stored. One answer per
  conversation is in flight at a time (409 otherwise). Answers left `PENDING` by a restart
  become `FAILED` and can be retried.
- **Context assembly:** the top `RAG_TOP_K` passages at or above `RAG_MIN_SCORE`, within
  `RAG_MAX_CONTEXT_CHARS`. If none qualify, the turn abstains without calling the model.
- **Untrusted documents:** passages are placed in numbered `<source>` blocks. Anything in
  them that looks like a delimiter (`<source>`, `<question>`, `<system>`…) is neutralized.
  The system prompt says sources are data and instructions inside them must never be
  followed. Answers are rendered as plain text (never HTML).
- **Structured output:** Ollama is constrained to the JSON schema
  `{answerable, answer, citations: [{source, quote}]}`.
- **Verification:** each claimed citation must name a source that was actually provided,
  and its quote (≥ `RAG_MIN_QUOTE_CHARS`) must occur in that passage, ignoring case,
  whitespace and quote styles; elided quotes must match segment by segment. Unverified
  citations are dropped and their `[n]` markers removed, and the rest are renumbered.
- **Abstention:** the model said the sources are insufficient, or nothing it cited could be
  verified. Its text is then not shown; the fixed abstention text is returned instead.
- **Evidence is stored, not reconstructed:** each citation snapshots the passage, so past
  answers stay auditable after their document is re-ingested or deleted. Deleting a
  conversation cascades to its messages and citations, and deleting a workspace cascades
  to its conversations.
- **Answers are not streamed:** an answer is verified as a whole before display, so
  token-by-token SSE streaming is deferred. The UI shows progress while it waits.

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

### Research agent design

- **Modules:** Research (runs, state machine, planning, synthesis) → Agent (`StepAgent`, the
  bounded per-step loop) → Tool (typed `AgentTool` interface + `ToolRegistry`) → Search /
  Ingestion / Document published APIs. Research stores report citations through Citation.
  V8 adds `research_*` tables and lets a citation belong to a message *or* a research run.
- **State machine:** `QUEUED → PLANNING → RUNNING → SYNTHESIZING → COMPLETED |
  INSUFFICIENT_EVIDENCE`; any active state can go to `FAILED` or `CANCELLED`, and those to
  `QUEUED` again (resume). Runs are claimed with a conditional update, so one executor owns a
  run. Every transition, step, tool call and passage is written as it happens.
- **Tools:** only `search_knowledge {query, limit}` (passages at or above `RAG_MIN_SCORE`)
  and `retrieve_source {passage: "P3", context}` (the passage plus neighbouring chunks). No
  shell, no web, no code execution. Arguments are bound to typed records and validated;
  unknown tools or bad arguments are recorded as `REJECTED`. The workspace comes from the
  run, never from the model, and `retrieve_source` only resolves handles this run has seen.
- **Bounds:** steps, actions per step, run-wide tool calls, sources and wall-clock time are
  capped. Duplicate calls are rejected (also after a resume); repeated duplicates, invalid
  decisions or repeated tool failures end the step, falling back to the planned queries.
  Cancellation and the deadline are checked before every action.
- **Reliability:** a model outage or timeout fails the run with a reason; tools failing
  with no evidence fail it; no relevant passages → `INSUFFICIENT_EVIDENCE`. Runs interrupted
  by a restart resume on startup (up to `RESEARCH_MAX_RESUMES`), keeping their plan,
  finished steps and sources.
- **Report:** the model writes a JSON report from the gathered passages (untrusted, in
  delimited blocks), and each `[n]` citation is verified exactly as in chat. Paragraphs
  without a verified citation are labelled "uncited" in the UI. With no verifiable
  citation, the run ends `INSUFFICIENT_EVIDENCE` and shows no report text.

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
- `ChatApiIntegrationTest`: grounded answers with exact document/chunk/page provenance,
  persistence and history, workspace isolation (API and prompt contents), abstention (no
  evidence, model says insufficient, fabricated citations), partial verification, model
  outage, timeout, malformed output and embedding outage with retry, one answer at a time,
  restart recovery, validation, cascades, and citations surviving document deletion. Uses
  a deterministic test-only chat model.
- `CitationValidatorTest`, `PromptAndParsingTest`: verification rules, marker renumbering,
  delimiter-injection neutralization, tolerant JSON parsing
- `OllamaRagChatTest`: end to end with the **real** `qwen2.5:7b`. It checks a cited
  grounded answer, an abstention on an off-topic question, and that a planted "ignore
  previous instructions" is not obeyed. Skipped automatically if the models are missing.
- `ResearchApiIntegrationTest`, `ResearchTimeLimitIntegrationTest`: planning → tool calls →
  cited report with chunk/page provenance, persistence, tool rejection and failure, model
  outage, insufficient evidence, cancellation, resume, restart recovery, workspace
  isolation, time limit. Uses scripted test-only models.
- `StepAgentTest`, `ToolRegistryTest`, `ResearchPlannerTest`, `ResearchStatusTest`: tool
  selection, loop bounds, duplicates (also across a resume), fallbacks, argument
  validation, plan cleanup, legal transitions
- `OllamaResearchTest`: a full run with the **real** models (skipped if unavailable)
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
  platform/ai/              # EmbeddingService, GenerationService: Spring AI / Ollama adapters
  chat/                     # conversations, messages, turns; uses RagService, CitationService
  rag/                      # retrieval → context assembly → generation → verification
  citation/                 # citation verification and persistence (evidence linkage)
  tool/                     # typed agent tools + registry (search_knowledge, retrieve_source)
  agent/                    # StepAgent: bounded tool-using loop for one plan step
  research/                 # runs, state machine, planner, orchestrator, synthesizer, API
backend/src/main/resources/
  application.yml
  db/migration/             # Flyway, versioned and forward-only
frontend/src/
  app/                      # routes: / (dashboard), /workspaces/[workspaceId], …/chat, …/research
  lib/api/                  # typed API client mirroring the backend DTOs
  components/ui/            # buttons, fields, modal, states, pagination
  components/{layout,workspaces,documents,chat,research}/
```

Business modules (`auth`, `user`, `workspace`, `document`, …) are added as packages under
`com.nexusai` as each is implemented, per
[module-architecture.md](docs/architecture/module-architecture.md).
