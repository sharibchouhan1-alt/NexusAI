import { apiRequest } from "./client";
import type { Page, Uuid, Workspace, WorkspaceRequest } from "./types";

const base = "/api/workspaces";

export const workspacesApi = {
  list: (page = 0, size = 20, signal?: AbortSignal) =>
    apiRequest<Page<Workspace>>(base, { query: { page, size }, signal }),

  get: (id: Uuid, signal?: AbortSignal) => apiRequest<Workspace>(`${base}/${id}`, { signal }),

  create: (request: WorkspaceRequest) =>
    apiRequest<Workspace>(base, { method: "POST", body: request }),

  update: (id: Uuid, request: WorkspaceRequest) =>
    apiRequest<Workspace>(`${base}/${id}`, { method: "PUT", body: request }),

  /** Fails with 409 while the workspace still holds documents. */
  remove: (id: Uuid) => apiRequest<void>(`${base}/${id}`, { method: "DELETE" }),
};
