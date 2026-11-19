import { apiRequest, apiUpload, type UploadOptions } from "./client";
import type {
  CreateDocumentRequest,
  DocumentChunk,
  DocumentMetadata,
  Page,
  UpdateDocumentRequest,
  Uuid,
} from "./types";

const base = (workspaceId: Uuid) => `/api/workspaces/${workspaceId}/documents`;

/** Document metadata, always addressed through its workspace. */
export const documentsApi = {
  list: (workspaceId: Uuid, page = 0, size = 20, signal?: AbortSignal) =>
    apiRequest<Page<DocumentMetadata>>(base(workspaceId), { query: { page, size }, signal }),

  get: (workspaceId: Uuid, documentId: Uuid, signal?: AbortSignal) =>
    apiRequest<DocumentMetadata>(`${base(workspaceId)}/${documentId}`, { signal }),

  /** Metadata-only registration (no file). */
  create: (workspaceId: Uuid, request: CreateDocumentRequest) =>
    apiRequest<DocumentMetadata>(base(workspaceId), { method: "POST", body: request }),

  /**
   * Uploads a PDF/TXT. Resolves (HTTP 202) once the file is stored; the document is then
   * PENDING and ingestion continues in the background.
   */
  upload: (workspaceId: Uuid, file: File, title: string | null, options?: UploadOptions) => {
    const form = new FormData();
    form.append("file", file);
    if (title) form.append("title", title);
    return apiUpload<DocumentMetadata>(base(workspaceId), form, options);
  },

  update: (workspaceId: Uuid, documentId: Uuid, request: UpdateDocumentRequest) =>
    apiRequest<DocumentMetadata>(`${base(workspaceId)}/${documentId}`, {
      method: "PUT",
      body: request,
    }),

  remove: (workspaceId: Uuid, documentId: Uuid) =>
    apiRequest<void>(`${base(workspaceId)}/${documentId}`, { method: "DELETE" }),

  // --- ingestion ---

  chunks: (workspaceId: Uuid, documentId: Uuid, page = 0, size = 20, signal?: AbortSignal) =>
    apiRequest<Page<DocumentChunk>>(`${base(workspaceId)}/${documentId}/chunks`, {
      query: { page, size },
      signal,
    }),

  /** Rebuilds the search index (embeddings) of a READY document; 409 while indexing. */
  reindex: (workspaceId: Uuid, documentId: Uuid) =>
    apiRequest<DocumentMetadata>(`${base(workspaceId)}/${documentId}/embeddings`, { method: "POST" }),

  /** Re-runs ingestion of the stored file (409 if one is already in progress). */
  reingest: (workspaceId: Uuid, documentId: Uuid) =>
    apiRequest<DocumentMetadata>(`${base(workspaceId)}/${documentId}/ingestion`, { method: "POST" }),
};
