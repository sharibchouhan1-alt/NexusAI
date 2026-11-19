import { apiRequest } from "./client";
import type { SearchResponse, Uuid } from "./types";

export const searchApi = {
  /**
   * Semantic search within one workspace. Fails with status 503 (retryable) while the
   * local embedding service (Ollama) is unavailable.
   */
  search: (workspaceId: Uuid, query: string, limit?: number, signal?: AbortSignal) =>
    apiRequest<SearchResponse>(`/api/workspaces/${workspaceId}/search`, {
      query: { q: query, limit },
      signal,
    }),
};
