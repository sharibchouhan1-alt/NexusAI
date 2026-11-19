import type { ProblemDetail } from "./types";

export const API_BASE_URL = (
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080"
).replace(/\/+$/, "");

/** Any failed API call: HTTP error (with problem details) or network failure (status 0). */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | null;
  /** Field-level validation messages keyed by request field name. */
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, message: string, problem: ProblemDetail | null = null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
    this.fieldErrors = Object.fromEntries(
      (problem?.errors ?? []).map((e) => [e.field, e.message]),
    );
  }

  get isNotFound() {
    return this.status === 404;
  }

  get isNetworkError() {
    return this.status === 0;
  }
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;
  return new ApiError(0, error instanceof Error ? error.message : "Unexpected error");
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  query?: Record<string, string | number | undefined>;
  signal?: AbortSignal;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, query, signal } = options;

  const url = new URL(API_BASE_URL + path);
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined) url.searchParams.set(key, String(value));
  }

  let response: Response;
  try {
    response = await fetch(url, {
      method,
      signal,
      cache: "no-store",
      headers: {
        Accept: "application/json, application/problem+json",
        ...(body !== undefined && { "Content-Type": "application/json" }),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch (error) {
    if (signal?.aborted) throw error;
    throw new ApiError(0, `Cannot reach the NexusAI API at ${API_BASE_URL}. Is the backend running?`);
  }

  if (response.status === 204) return undefined as T;

  const payload: unknown = await response.json().catch(() => null);

  if (!response.ok) {
    const problem = isProblem(payload) ? payload : null;
    throw new ApiError(
      response.status,
      problem?.detail ?? problem?.title ?? `Request failed (${response.status})`,
      problem,
    );
  }
  return payload as T;
}

function isProblem(value: unknown): value is ProblemDetail {
  return typeof value === "object" && value !== null && "status" in value;
}
