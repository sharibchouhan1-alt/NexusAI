import { apiRequest } from "./client";
import type { HealthReport } from "./types";

export const healthApi = {
  get: (signal?: AbortSignal) => apiRequest<HealthReport>("/api/health", { signal }),
};
