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

/** com.nexusai.document.DocumentStatus */
export type DocumentStatus = "REGISTERED";

/** com.nexusai.document.DocumentResponse */
export interface DocumentMetadata {
  id: Uuid;
  workspaceId: Uuid;
  title: string;
  sourceType: DocumentSourceType;
  mediaType: string | null;
  origin: string | null;
  status: DocumentStatus;
  createdAt: Instant;
  updatedAt: Instant;
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
} as const;

/** Same pattern as CreateDocumentRequest.mediaType. */
export const MEDIA_TYPE_PATTERN = /^[\w.+-]+\/[\w.+-]+$/;
