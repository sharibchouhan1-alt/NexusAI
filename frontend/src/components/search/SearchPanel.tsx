"use client";

import { useRef, useState, type FormEvent } from "react";
import { ApiError, toApiError } from "@/lib/api/client";
import { searchApi } from "@/lib/api/search";
import { LIMITS, type SearchResponse, type SearchResult, type Uuid } from "@/lib/api/types";
import { Button } from "@/components/ui/Button";
import { AlertIcon, FileIcon, RefreshIcon, SearchIcon } from "@/components/ui/icons";
import { EmptyState, Skeleton } from "@/components/ui/states";

type State =
  | { kind: "idle" }
  | { kind: "loading"; query: string }
  | { kind: "done"; response: SearchResponse }
  | { kind: "error"; query: string; error: ApiError };

/**
 * Semantic search over the workspace's indexed passages. Results are ranked passages with
 * provenance; nothing is generated. Render with key={workspaceId} so state never leaks
 * between workspaces.
 */
export function SearchPanel({ workspaceId }: { workspaceId: Uuid }) {
  const [input, setInput] = useState("");
  const [state, setState] = useState<State>({ kind: "idle" });
  const [validation, setValidation] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  async function run(query: string) {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setState({ kind: "loading", query });
    try {
      const response = await searchApi.search(workspaceId, query, LIMITS.searchDefaultLimit, controller.signal);
      if (!controller.signal.aborted) setState({ kind: "done", response });
    } catch (error) {
      if (!controller.signal.aborted) setState({ kind: "error", query, error: toApiError(error) });
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = input.trim();
    if (!query) {
      setValidation("Enter something to search for");
      return;
    }
    if (query.length > LIMITS.searchQuery) {
      setValidation(`Queries are limited to ${LIMITS.searchQuery} characters`);
      return;
    }
    setValidation(null);
    void run(query);
  }

  const loading = state.kind === "loading";

  return (
    <section aria-labelledby="search-heading" className="mb-10">
      <h2 id="search-heading" className="mb-3 text-lg font-semibold text-zinc-900 dark:text-zinc-100">
        Search
      </h2>
      <form onSubmit={submit} noValidate role="search" className="flex flex-col gap-2 sm:flex-row">
        <div className="relative flex-1">
          <SearchIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-zinc-400" />
          <input
            type="search"
            name="q"
            value={input}
            onChange={(e) => {
              setInput(e.target.value);
              setValidation(null);
            }}
            placeholder="Find passages by meaning, e.g. “how do plants make energy?”"
            aria-label="Search this workspace"
            aria-invalid={validation ? true : undefined}
            aria-describedby={validation ? "search-validation" : undefined}
            maxLength={LIMITS.searchQuery + 1}
            autoComplete="off"
            className={`block h-10 w-full rounded-lg border-0 bg-white pr-3 pl-9 text-sm text-zinc-900 shadow-sm ring-1 ring-inset placeholder:text-zinc-400 focus:ring-2 focus:ring-inset focus:outline-none dark:bg-zinc-900 dark:text-zinc-100 ${
              validation ? "ring-red-400 focus:ring-red-500" : "ring-zinc-300 focus:ring-indigo-600 dark:ring-zinc-700"
            }`}
          />
        </div>
        <Button type="submit" loading={loading} className="h-10 sm:w-28">
          Search
        </Button>
      </form>
      {validation && (
        <p id="search-validation" className="mt-1.5 text-xs font-medium text-red-600 dark:text-red-400">
          {validation}
        </p>
      )}

      <div className="mt-5" aria-live="polite">
        {state.kind === "idle" && (
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            Semantic search compares meaning, not exact words. Embeddings are computed locally with Ollama
            (nomic-embed-text); results are the most similar passages from this workspace only.
          </p>
        )}

        {state.kind === "loading" && (
          <div className="space-y-3" role="status" aria-label="Searching">
            {Array.from({ length: 3 }, (_, i) => (
              <Skeleton key={i} className="h-24" />
            ))}
          </div>
        )}

        {state.kind === "error" && <SearchError error={state.error} onRetry={() => run(state.query)} />}

        {state.kind === "done" && <SearchResults response={state.response} />}
      </div>
    </section>
  );
}

function SearchError({ error, onRetry }: { error: ApiError; onRetry: () => void }) {
  const unavailable = error.status === 503;
  return (
    <div
      role="alert"
      className={`flex flex-col gap-3 rounded-xl border p-4 sm:flex-row sm:items-start ${
        unavailable
          ? "border-amber-200 bg-amber-50 dark:border-amber-900/60 dark:bg-amber-950/30"
          : "border-red-200 bg-red-50 dark:border-red-900/60 dark:bg-red-950/30"
      }`}
    >
      <AlertIcon className={`mt-0.5 size-5 shrink-0 ${unavailable ? "text-amber-600" : "text-red-600"}`} />
      <div className="flex-1 text-sm">
        <p className="font-medium text-zinc-900 dark:text-zinc-100">
          {unavailable ? "Search is temporarily unavailable" : "Search failed"}
        </p>
        <p className="mt-0.5 text-zinc-600 dark:text-zinc-300">
          {unavailable
            ? "The local embedding service (Ollama) isn't responding. Start it with `ollama serve`, then try again. Uploads keep working and will be indexed automatically."
            : error.message}
        </p>
      </div>
      <Button variant="secondary" size="sm" icon={<RefreshIcon />} onClick={onRetry}>
        Try again
      </Button>
    </div>
  );
}

function SearchResults({ response }: { response: SearchResponse }) {
  const { results, coverage } = response;
  const notice =
    coverage.pending > 0 || coverage.failed > 0
      ? [
          coverage.pending > 0 &&
            `${coverage.pending} ${coverage.pending === 1 ? "document is" : "documents are"} still being indexed`,
          coverage.failed > 0 && `${coverage.failed} couldn't be indexed`,
        ]
          .filter(Boolean)
          .join(" and ") + ", so results may be incomplete."
      : null;

  return (
    <>
      {notice && (
        <p className="mb-3 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:bg-amber-950/30 dark:text-amber-200">
          {notice}
        </p>
      )}
      {results.length === 0 ? (
        <EmptyState
          icon={<SearchIcon className="size-5" />}
          title={coverage.indexed === 0 ? "Nothing to search yet" : "No matching passages"}
          description={
            coverage.indexed === 0
              ? "Upload a PDF or text file. It becomes searchable once its embeddings are computed."
              : "Try describing what you're looking for in different words."
          }
        />
      ) : (
        <>
          <p className="mb-3 text-xs text-zinc-500 dark:text-zinc-400">
            {results.length} most similar {results.length === 1 ? "passage" : "passages"} for{" "}
            <span className="font-medium text-zinc-700 dark:text-zinc-300">“{response.query}”</span> ·{" "}
            {coverage.indexed} indexed {coverage.indexed === 1 ? "document" : "documents"}
          </p>
          <ol className="space-y-3">
            {results.map((result) => (
              <ResultCard key={result.chunkId} result={result} />
            ))}
          </ol>
        </>
      )}
    </>
  );
}

function ResultCard({ result }: { result: SearchResult }) {
  const [expanded, setExpanded] = useState(false);
  const long = result.content.length > 420;
  const similarity = Math.max(0, Math.min(1, result.score));

  return (
    <li className="rounded-xl border border-zinc-200 bg-white p-4 shadow-xs dark:border-zinc-800 dark:bg-zinc-900">
      <div className="mb-2 flex flex-wrap items-center gap-x-3 gap-y-1.5">
        <span className="flex size-6 items-center justify-center rounded-full bg-indigo-50 text-xs font-semibold text-indigo-700 tabular-nums dark:bg-indigo-500/10 dark:text-indigo-300">
          {result.rank}
        </span>
        <span className="flex min-w-0 items-center gap-1.5 text-sm font-medium text-zinc-900 dark:text-zinc-100">
          <FileIcon className="size-4 text-zinc-400" />
          <span className="truncate" title={result.documentOrigin ?? result.documentTitle}>
            {result.documentTitle}
          </span>
        </span>
        {result.pageNumber !== null && (
          <span className="rounded bg-zinc-100 px-1.5 py-0.5 text-xs font-medium text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300">
            Page {result.pageNumber}
          </span>
        )}
        <span className="ml-auto flex items-center gap-2 text-xs text-zinc-500 dark:text-zinc-400" title="Cosine similarity">
          <span className="h-1.5 w-16 overflow-hidden rounded-full bg-zinc-200 dark:bg-zinc-800" aria-hidden="true">
            <span className="block h-full rounded-full bg-indigo-500" style={{ width: `${Math.round(similarity * 100)}%` }} />
          </span>
          <span className="tabular-nums">similarity {result.score.toFixed(2)}</span>
        </span>
      </div>
      <p
        className={`text-sm leading-relaxed break-words whitespace-pre-wrap text-zinc-700 dark:text-zinc-300 ${
          long && !expanded ? "line-clamp-5" : ""
        }`}
      >
        {result.content}
      </p>
      <div className="mt-2 flex items-center gap-3 text-xs text-zinc-400">
        <span className="font-mono tabular-nums">
          chunk #{result.chunkIndex + 1} · chars {result.charStart}–{result.charEnd}
        </span>
        {long && (
          <button
            type="button"
            onClick={() => setExpanded((v) => !v)}
            className="font-medium text-indigo-600 hover:underline dark:text-indigo-400"
          >
            {expanded ? "Show less" : "Show more"}
          </button>
        )}
      </div>
    </li>
  );
}
