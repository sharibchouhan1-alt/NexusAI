// Mirrors the Spring Boot DTOs in backend/src/main/java/com/nexusai/**.

/** ISO-8601 instant, e.g. "2026-11-19T03:39:07.610363Z". */
export type Instant = string;
export type Uuid = string;

/** com.nexusai.platform.web.PageResponse */
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

/** RFC 9457 problem details, as produced by ApiExceptionHandler. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  errors?: { field: string; message: string }[];
}

// --- Workspace module ---

/** com.nexusai.workspace.WorkspaceResponse */
export interface Workspace {
  id: Uuid;
  name: string;
  description: string | null;
  createdAt: Instant;
  updatedAt: Instant;
}

/** com.nexusai.workspace.WorkspaceRequest (create and replace) */
export interface WorkspaceRequest {
  name: string;
  description?: string | null;
}

// --- Document module ---

/** com.nexusai.document.DocumentSourceType */
export type DocumentSourceType = "FILE" | "URL";

/**
 * com.nexusai.document.DocumentStatus. REGISTERED = metadata only (no file); the others
 * describe ingestion of an uploaded file.
 */
export type DocumentStatus = "REGISTERED" | "PENDING" | "PROCESSING" | "READY" | "FAILED";

export const IN_FLIGHT_STATUSES: readonly DocumentStatus[] = ["PENDING", "PROCESSING"];

/** com.nexusai.document.DocumentResponse */
export interface DocumentMetadata {
  id: Uuid;
  workspaceId: Uuid;
  title: string;
  sourceType: DocumentSourceType;
  mediaType: string | null;
  origin: string | null;
  status: DocumentStatus;
  /** User-facing failure reason when status is FAILED. */
  statusDetail: string | null;
  fileSizeBytes: number | null;
  /** PDF page count; null for plain text and metadata-only documents. */
  pageCount: number | null;
  chunkCount: number | null;
  /** Search readiness of a READY document; null before its chunks exist. */
  indexStatus: SearchIndexStatus | null;
  /** Note while waiting for the embedding service, or the reason indexing failed. */
  indexDetail: string | null;
  createdAt: Instant;
  updatedAt: Instant;
}

/** com.nexusai.document.SearchIndexStatus */
export type SearchIndexStatus = "PENDING" | "INDEXING" | "INDEXED" | "FAILED";

/** com.nexusai.document.SearchCoverage */
export interface SearchCoverage {
  indexed: number;
  pending: number;
  failed: number;
}

/** com.nexusai.search.SearchResponse.Result */
export interface SearchResult {
  rank: number;
  /** Cosine similarity, higher is closer (typically 0.3–0.9 for nomic-embed-text). */
  score: number;
  documentId: Uuid;
  documentTitle: string;
  documentOrigin: string | null;
  mediaType: string | null;
  chunkId: Uuid;
  chunkIndex: number;
  pageNumber: number | null;
  charStart: number;
  charEnd: number;
  content: string;
}

/** com.nexusai.search.SearchResponse */
export interface SearchResponse {
  query: string;
  model: string;
  results: SearchResult[];
  coverage: SearchCoverage;
}

/** com.nexusai.ingestion.ChunkResponse */
export interface DocumentChunk {
  id: Uuid;
  index: number;
  /** 1-based PDF page; null for plain text. */
  pageNumber: number | null;
  charStart: number;
  charEnd: number;
  content: string;
}

/** com.nexusai.document.CreateDocumentRequest */
export interface CreateDocumentRequest {
  title: string;
  sourceType: DocumentSourceType;
  mediaType?: string | null;
  origin?: string | null;
}

/** com.nexusai.document.UpdateDocumentRequest */
export interface UpdateDocumentRequest {
  title: string;
}

/** com.nexusai.platform.health.HealthReport */
export interface HealthReport {
  status: "UP" | "DOWN";
  service: string;
  timestamp: Instant;
  database: { status: "UP" | "DOWN"; pgvector: string | null };
}

/** Server-side limits (bean validation on the request DTOs). */
export const LIMITS = {
  workspaceName: 200,
  workspaceDescription: 2000,
  documentTitle: 500,
  mediaType: 255,
  origin: 2048,
  pageSizeMax: 100,
  /** Backend default MAX_UPLOAD_SIZE (25MB). The server enforces the real limit (413). */
  uploadMaxBytes: 25 * 1024 * 1024,
  /** SearchController: q max length, default and max result count. */
  searchQuery: 1000,
  searchDefaultLimit: 10,
  searchMaxLimit: 50,
} as const;

/** File types the upload endpoint accepts (checked by extension and content server-side). */
export const UPLOAD_EXTENSIONS = [".pdf", ".txt"] as const;

/** Same pattern as CreateDocumentRequest.mediaType. */
export const MEDIA_TYPE_PATTERN = /^[\w.+-]+\/[\w.+-]+$/;

// --- Chat module (com.nexusai.chat.ChatDtos) and Citation module ---

/** com.nexusai.citation.CitationView: verified evidence behind an answer. */
export interface Citation {
  /** The [n] marker number in the answer text. */
  ordinal: number;
  sourceKind: "DOCUMENT_CHUNK";
  documentId: Uuid;
  chunkId: Uuid;
  documentTitle: string;
  /** 1-based PDF page; null for plain text. */
  pageNumber: number | null;
  chunkIndex: number;
  charStart: number;
  charEnd: number;
  /** Verified to occur in `passage`. */
  quote: string;
  /** The cited chunk text as it was when the answer was produced. */
  passage: string;
  /** Retrieval similarity of the passage to the question. */
  score: number;
}

/** com.nexusai.chat.MessageStatus */
export type MessageStatus = "COMPLETE" | "PENDING" | "ABSTAINED" | "FAILED";

/** com.nexusai.chat.ChatDtos.MessageView */
export interface ChatMessage {
  id: Uuid;
  conversationId: Uuid;
  role: "USER" | "ASSISTANT";
  /** Question, verified answer with [n] markers, or the abstention text. */
  content: string;
  status: MessageStatus;
  /** Why an answer abstained or failed. */
  statusDetail: string | null;
  model: string | null;
  retrievedCount: number | null;
  latencyMs: number | null;
  createdAt: Instant;
  citations: Citation[];
}

/** com.nexusai.chat.ChatDtos.ConversationSummary */
export interface ConversationSummary {
  id: Uuid;
  workspaceId: Uuid;
  title: string;
  messageCount: number;
  createdAt: Instant;
  updatedAt: Instant;
}

/** com.nexusai.chat.ChatDtos.ConversationDetail */
export interface ConversationDetail {
  id: Uuid;
  workspaceId: Uuid;
  title: string;
  createdAt: Instant;
  updatedAt: Instant;
  messages: ChatMessage[];
}

/** com.nexusai.chat.ChatDtos.TurnResponse */
export interface Turn {
  question: ChatMessage;
  answer: ChatMessage;
}

export const CHAT_LIMITS = { question: 4000, title: 200 } as const;

// --- Research module (com.nexusai.research.ResearchDtos) ---

/** com.nexusai.research.ResearchStatus */
export type ResearchStatus =
  | "QUEUED"
  | "PLANNING"
  | "RUNNING"
  | "SYNTHESIZING"
  | "COMPLETED"
  | "INSUFFICIENT_EVIDENCE"
  | "FAILED"
  | "CANCELLED";

export const ACTIVE_RESEARCH: readonly ResearchStatus[] = ["QUEUED", "PLANNING", "RUNNING", "SYNTHESIZING"];

export type ResearchStepStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED" | "SKIPPED";
export type ToolCallStatus = "SUCCEEDED" | "FAILED" | "REJECTED";

export interface ResearchToolCall {
  seq: number;
  tool: string;
  /** Arguments as the model proposed them (JSON text; data, never executed). */
  arguments: string;
  reason: string | null;
  status: ToolCallStatus;
  resultSummary: string | null;
  error: string | null;
  durationMs: number;
  createdAt: Instant;
}

export interface ResearchStep {
  id: Uuid;
  ordinal: number;
  title: string;
  goal: string;
  plannedQueries: string[];
  status: ResearchStepStatus;
  statusDetail: string | null;
  actionCount: number;
  startedAt: Instant | null;
  finishedAt: Instant | null;
  toolCalls: ResearchToolCall[];
}

/** A gathered passage; `handle` (P1, P2, ...) is how the agent referred to it. */
export interface ResearchSource {
  handle: string;
  documentId: Uuid;
  chunkId: Uuid;
  documentTitle: string;
  pageNumber: number | null;
  chunkIndex: number;
  charStart: number;
  charEnd: number;
  score: number | null;
  stepOrdinal: number;
  toolCallSeq: number;
  content: string;
}

export interface ResearchReport {
  title: string;
  /** Verified text: summary, then "## Heading" sections, with [n] markers. */
  text: string;
  partial: boolean;
  citations: Citation[];
}

export interface ResearchRunSummary {
  id: Uuid;
  workspaceId: Uuid;
  objective: string;
  status: ResearchStatus;
  statusDetail: string | null;
  cancelRequested: boolean;
  toolCallCount: number;
  sourceCount: number;
  reportTitle: string | null;
  createdAt: Instant;
  startedAt: Instant | null;
  finishedAt: Instant | null;
}

export interface ResearchRunDetail extends Omit<ResearchRunSummary, "reportTitle"> {
  resumeCount: number;
  planOrigin: "MODEL" | "FALLBACK" | null;
  model: string | null;
  steps: ResearchStep[];
  sources: ResearchSource[];
  report: ResearchReport | null;
  gaps: string[];
  limits: { maxSteps: number; maxActionsPerStep: number; maxToolCalls: number; maxSources: number; maxDurationSeconds: number };
}

export const RESEARCH_LIMITS = { objectiveMin: 10, objectiveMax: 2000 } as const;
