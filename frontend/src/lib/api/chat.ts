import { apiRequest } from "./client";
import type { ConversationDetail, ConversationSummary, Page, Turn, Uuid } from "./types";

const base = (workspaceId: Uuid) => `/api/workspaces/${workspaceId}/conversations`;

/** Workspace-scoped chat. Answers are generated locally and may take tens of seconds. */
export const chatApi = {
  list: (workspaceId: Uuid, page = 0, size = 50, signal?: AbortSignal) =>
    apiRequest<Page<ConversationSummary>>(base(workspaceId), { query: { page, size }, signal }),

  get: (workspaceId: Uuid, conversationId: Uuid, signal?: AbortSignal) =>
    apiRequest<ConversationDetail>(`${base(workspaceId)}/${conversationId}`, { signal }),

  create: (workspaceId: Uuid, title?: string) =>
    apiRequest<ConversationSummary>(base(workspaceId), { method: "POST", body: title ? { title } : {} }),

  rename: (workspaceId: Uuid, conversationId: Uuid, title: string) =>
    apiRequest<ConversationSummary>(`${base(workspaceId)}/${conversationId}`, { method: "PUT", body: { title } }),

  remove: (workspaceId: Uuid, conversationId: Uuid) =>
    apiRequest<void>(`${base(workspaceId)}/${conversationId}`, { method: "DELETE" }),

  /** Resolves when the answer is COMPLETE, ABSTAINED or FAILED (all persisted). 409 if one is pending. */
  ask: (workspaceId: Uuid, conversationId: Uuid, content: string) =>
    apiRequest<Turn>(`${base(workspaceId)}/${conversationId}/messages`, { method: "POST", body: { content } }),

  /** Regenerates a FAILED answer (must be the conversation's latest message). */
  retry: (workspaceId: Uuid, conversationId: Uuid, messageId: Uuid) =>
    apiRequest<Turn>(`${base(workspaceId)}/${conversationId}/messages/${messageId}/retry`, { method: "POST" }),
};
