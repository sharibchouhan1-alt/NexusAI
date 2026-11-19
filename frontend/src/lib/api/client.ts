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

  if (!response.ok) throw errorFromPayload(response.status, payload);
  return payload as T;
}

export interface UploadOptions {
  /** Called with the fraction (0–1) of the request body sent so far. */
  onProgress?: (fraction: number) => void;
  signal?: AbortSignal;
}

/**
 * Multipart POST with upload progress. fetch() cannot report upload progress, so this uses
 * XMLHttpRequest; errors are parsed exactly like apiRequest's. Aborting rejects with an
 * AbortError DOMException.
 */
export function apiUpload<T>(path: string, form: FormData, { onProgress, signal }: UploadOptions = {}): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException("Upload cancelled", "AbortError"));
      return;
    }
    const xhr = new XMLHttpRequest();
    xhr.open("POST", API_BASE_URL + path);
    xhr.setRequestHeader("Accept", "application/json, application/problem+json");

    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress?.(event.loaded / event.total);
    };
    xhr.onload = () => {
      let payload: unknown = null;
      try {
        payload = xhr.responseText ? JSON.parse(xhr.responseText) : null;
      } catch {
        // non-JSON body: fall through to the generic message
      }
      if (xhr.status >= 200 && xhr.status < 300) resolve(payload as T);
      else reject(errorFromPayload(xhr.status, payload));
    };
    xhr.onerror = () =>
      reject(
        new ApiError(
          0,
          `The upload could not be completed. Check that the NexusAI API at ${API_BASE_URL} is running and the file is within the size limit.`,
        ),
      );
    xhr.onabort = () => reject(new DOMException("Upload cancelled", "AbortError"));
    signal?.addEventListener("abort", () => xhr.abort(), { once: true });

    // Let the browser set the multipart boundary; never set Content-Type manually here.
    xhr.send(form);
  });
}

export const isAbortError = (error: unknown) =>
  error instanceof DOMException && error.name === "AbortError";

function errorFromPayload(status: number, payload: unknown): ApiError {
  const problem = isProblem(payload) ? payload : null;
  return new ApiError(status, problem?.detail ?? problem?.title ?? `Request failed (${status})`, problem);
}

function isProblem(value: unknown): value is ProblemDetail {
  return typeof value === "object" && value !== null && "status" in value;
}
