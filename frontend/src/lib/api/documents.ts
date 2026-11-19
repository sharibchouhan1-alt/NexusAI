import { apiRequest } from "./client";
import type {
  CreateDocumentRequest,
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

  create: (workspaceId: Uuid, request: CreateDocumentRequest) =>
    apiRequest<DocumentMetadata>(base(workspaceId), { method: "POST", body: request }),

  update: (workspaceId: Uuid, documentId: Uuid, request: UpdateDocumentRequest) =>
    apiRequest<DocumentMetadata>(`${base(workspaceId)}/${documentId}`, {
      method: "PUT",
      body: request,
    }),

  remove: (workspaceId: Uuid, documentId: Uuid) =>
    apiRequest<void>(`${base(workspaceId)}/${documentId}`, { method: "DELETE" }),
};
