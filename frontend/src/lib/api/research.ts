import { apiRequest } from "./client";
import type { Page, ResearchRunDetail, ResearchRunSummary, Uuid } from "./types";

const base = (workspaceId: Uuid) => `/api/workspaces/${workspaceId}/research`;

/** Workspace-scoped research runs. Starting returns at once; poll `get` for progress. */
export const researchApi = {
  list: (workspaceId: Uuid, page = 0, size = 50, signal?: AbortSignal) =>
    apiRequest<Page<ResearchRunSummary>>(base(workspaceId), { query: { page, size }, signal }),

  get: (workspaceId: Uuid, runId: Uuid, signal?: AbortSignal) =>
    apiRequest<ResearchRunDetail>(`${base(workspaceId)}/${runId}`, { signal }),

  start: (workspaceId: Uuid, objective: string) =>
    apiRequest<ResearchRunDetail>(base(workspaceId), { method: "POST", body: { objective } }),

  /** Queued runs stop at once; running ones at their next checkpoint. */
  cancel: (workspaceId: Uuid, runId: Uuid) =>
    apiRequest<ResearchRunDetail>(`${base(workspaceId)}/${runId}/cancel`, { method: "POST" }),

  /** Continues a FAILED or CANCELLED run where it stopped. */
  resume: (workspaceId: Uuid, runId: Uuid) =>
    apiRequest<ResearchRunDetail>(`${base(workspaceId)}/${runId}/resume`, { method: "POST" }),

  remove: (workspaceId: Uuid, runId: Uuid) =>
    apiRequest<void>(`${base(workspaceId)}/${runId}`, { method: "DELETE" }),
};
