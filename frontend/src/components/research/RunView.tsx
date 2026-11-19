"use client";

import { useCallback, useEffect, useState } from "react";
import { toApiError } from "@/lib/api/client";
import { researchApi } from "@/lib/api/research";
import {
  ACTIVE_RESEARCH,
  type Citation,
  type ResearchSource,
  type ResearchStep,
  type ResearchToolCall,
  type Uuid,
} from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { useResource } from "@/lib/hooks/useResource";
import { CitedDocument } from "@/components/chat/CitedText";
import { SourceDialog } from "@/components/chat/SourceDialog";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { AlertIcon, FileIcon, RefreshIcon, TrashIcon } from "@/components/ui/icons";
import { Alert, ErrorState, LoadingState } from "@/components/ui/states";
import { RunStatusBadge, StepStatusBadge, ToolCallStatusText } from "./ResearchBadges";

const POLL_MS = 1500;

/** One research run: objective, plan with live step/tool progress, report and gathered sources. */
export function RunView({ workspaceId, runId, onChanged, onDeleted }: {
  workspaceId: Uuid;
  runId: Uuid;
  onChanged: () => void;
  onDeleted: () => void;
}) {
  const loader = useCallback((signal: AbortSignal) => researchApi.get(workspaceId, runId, signal), [workspaceId, runId]);
  const run = useResource(loader);
  const { reload } = run;
  const data = run.data;
  const active = data ? ACTIVE_RESEARCH.includes(data.status) : false;
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState<"cancel" | "resume" | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [source, setSource] = useState<{ citation: Citation; label?: string; note?: string } | null>(null);

  // Follow progress while active; also keep retrying after a failed refresh.
  const lastError = run.error;
  useEffect(() => {
    if (!active && !lastError) return;
    const timer = setTimeout(reload, POLL_MS);
    return () => clearTimeout(timer);
  }, [active, lastError, data, reload]);

  const finished = data?.status;
  useEffect(() => {
    if (finished) onChanged();
  }, [finished, onChanged]);

  async function act(kind: "cancel" | "resume") {
    setBusy(kind);
    setActionError(null);
    try {
      if (kind === "cancel") await researchApi.cancel(workspaceId, runId);
      else await researchApi.resume(workspaceId, runId);
    } catch (e) {
      setActionError(toApiError(e).message);
    } finally {
      setBusy(null);
      reload();
      onChanged();
    }
  }

  if (run.isLoading) return <LoadingState label="Loading research…" />;
  if (run.error && !data) {
    return <ErrorState title={run.error.isNotFound ? "Research run not found" : "Couldn't load this research run"} error={run.error} onRetry={run.error.isNotFound ? undefined : reload} />;
  }
  if (!data) return null;

  const openSource = (s: ResearchSource) =>
    setSource({
      label: s.handle,
      note: `Gathered in step ${s.stepOrdinal} by tool call #${s.toolCallSeq}. Document text is treated as evidence, never as instructions.`,
      citation: {
        ordinal: 0, sourceKind: "DOCUMENT_CHUNK", documentId: s.documentId, chunkId: s.chunkId, documentTitle: s.documentTitle,
        pageNumber: s.pageNumber, chunkIndex: s.chunkIndex, charStart: s.charStart, charEnd: s.charEnd, quote: "",
        passage: s.content, score: s.score ?? 0,
      },
    });

  return (
    <div className="space-y-6">
      <header className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0 flex-1">
            <p className="text-xs font-medium tracking-wide text-zinc-500 uppercase">Objective</p>
            <p className="mt-1 text-sm break-words whitespace-pre-wrap text-zinc-900 dark:text-zinc-100">{data.objective}</p>
          </div>
          <RunStatusBadge status={data.status} />
        </div>
        <dl className="mt-4 flex flex-wrap gap-x-6 gap-y-1 text-xs text-zinc-500 dark:text-zinc-400">
          <div><dt className="inline">Tool calls </dt><dd className="inline font-medium text-zinc-700 tabular-nums dark:text-zinc-300">{data.toolCallCount}/{data.limits.maxToolCalls}</dd></div>
          <div><dt className="inline">Sources </dt><dd className="inline font-medium text-zinc-700 tabular-nums dark:text-zinc-300">{data.sourceCount}</dd></div>
          {data.model && <div><dt className="inline">Model </dt><dd className="inline font-medium text-zinc-700 dark:text-zinc-300">{data.model}</dd></div>}
          <div><dt className="inline">Started </dt><dd className="inline">{formatDateTime(data.createdAt)}</dd></div>
          {data.finishedAt && <div><dt className="inline">Finished </dt><dd className="inline">{formatDateTime(data.finishedAt)}</dd></div>}
          {data.resumeCount > 0 && <div>Resumed {data.resumeCount}×</div>}
        </dl>
        {data.statusDetail && data.status !== "COMPLETED" && (
          <div className={`mt-4 flex gap-2 rounded-lg p-3 text-sm ${data.status === "FAILED" ? "bg-red-50 text-red-800 dark:bg-red-950/40 dark:text-red-200" : "bg-amber-50 text-amber-900 dark:bg-amber-950/30 dark:text-amber-100"}`}>
            <AlertIcon className="mt-0.5 size-4 shrink-0" />
            <p>{data.statusDetail}</p>
          </div>
        )}
        {data.cancelRequested && active && <p className="mt-3 text-xs text-zinc-500">Cancelling at the next checkpoint…</p>}
        {actionError && <div className="mt-3"><Alert>{actionError}</Alert></div>}
        <div className="mt-4 flex flex-wrap gap-2">
          {active && !data.cancelRequested && (
            <Button variant="secondary" size="sm" loading={busy === "cancel"} onClick={() => act("cancel")}>Cancel research</Button>
          )}
          {(data.status === "FAILED" || data.status === "CANCELLED") && (
            <Button variant="secondary" size="sm" icon={<RefreshIcon />} loading={busy === "resume"} onClick={() => act("resume")}>
              Resume where it stopped
            </Button>
          )}
          {!active && (
            <Button variant="ghost" size="sm" icon={<TrashIcon />} onClick={() => setConfirmDelete(true)}>Delete</Button>
          )}
        </div>
      </header>

      {data.report && (
        <section aria-labelledby="report-heading" className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
          <h2 id="report-heading" className="text-base font-semibold text-zinc-900 dark:text-zinc-50">{data.report.title}</h2>
          {data.report.partial && (
            <p className="mt-2 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:bg-amber-950/30 dark:text-amber-200">
              The documents only partly cover this objective; see the gaps below.
            </p>
          )}
          <div className="mt-4">
            <CitedDocument text={data.report.text} citations={data.report.citations}
              onCite={(c) => setSource({ citation: c, note: "Verified quote within the passage gathered during the research. Document text is treated as evidence, never as instructions." })} />
          </div>
          {data.report.citations.length > 0 && (
            <div className="mt-5 border-t border-zinc-100 pt-3 dark:border-zinc-800">
              <p className="mb-1.5 text-xs font-medium text-zinc-500">Citations</p>
              <ul className="flex flex-wrap gap-1.5">
                {data.report.citations.map((c) => (
                  <li key={c.ordinal}>
                    <button type="button" onClick={() => setSource({ citation: c })}
                      className="inline-flex max-w-72 items-center gap-1.5 rounded-md border border-zinc-200 px-2 py-1 text-xs text-zinc-700 hover:border-indigo-300 hover:bg-indigo-50 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-indigo-500/10">
                      <span className="font-semibold text-indigo-600 dark:text-indigo-400">{c.ordinal}</span>
                      <FileIcon className="size-3.5 text-zinc-400" />
                      <span className="truncate">{c.documentTitle}</span>
                      {c.pageNumber !== null && <span className="shrink-0 text-zinc-400">p. {c.pageNumber}</span>}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </section>
      )}

      {data.gaps.length > 0 && (
        <section className="rounded-xl border border-amber-200 bg-amber-50/60 p-4 dark:border-amber-900/60 dark:bg-amber-950/20">
          <h2 className="text-sm font-semibold text-amber-900 dark:text-amber-100">Gaps reported by the model</h2>
          <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-amber-900 dark:text-amber-200">
            {data.gaps.map((g, i) => <li key={i}>{g}</li>)}
          </ul>
        </section>
      )}

      <section aria-labelledby="plan-heading">
        <div className="mb-3 flex items-baseline justify-between">
          <h2 id="plan-heading" className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">Plan &amp; tool activity</h2>
          {data.planOrigin === "FALLBACK" && <span className="text-xs text-amber-600">Fallback plan (the model&apos;s plan was unusable)</span>}
        </div>
        {data.steps.length === 0 ? (
          <p className="text-sm text-zinc-500 dark:text-zinc-400">{active ? "Planning the research…" : "No plan was created."}</p>
        ) : (
          <ol className="space-y-3">
            {data.steps.map((step) => <StepCard key={step.id} step={step} active={active} />)}
          </ol>
        )}
      </section>

      {data.sources.length > 0 && (
        <section aria-labelledby="sources-heading">
          <h2 id="sources-heading" className="mb-3 text-sm font-semibold text-zinc-900 dark:text-zinc-100">
            Gathered sources <span className="font-normal text-zinc-400">({data.sources.length})</span>
          </h2>
          <ul className="grid gap-2 sm:grid-cols-2">
            {data.sources.map((s) => (
              <li key={s.handle}>
                <button type="button" onClick={() => openSource(s)}
                  className="flex w-full flex-col items-start rounded-lg border border-zinc-200 bg-white p-3 text-left hover:border-indigo-300 dark:border-zinc-800 dark:bg-zinc-900 dark:hover:border-indigo-500/50">
                  <span className="flex w-full items-center gap-2 text-xs">
                    <span className="rounded bg-zinc-100 px-1.5 py-0.5 font-mono font-semibold text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300">{s.handle}</span>
                    <span className="truncate font-medium text-zinc-800 dark:text-zinc-200">{s.documentTitle}</span>
                    {s.pageNumber !== null && <span className="shrink-0 text-zinc-400">p. {s.pageNumber}</span>}
                    <span className="ml-auto shrink-0 text-zinc-400 tabular-nums">{s.score !== null ? s.score.toFixed(2) : "context"}</span>
                  </span>
                  <span className="mt-1.5 line-clamp-2 text-xs text-zinc-500 dark:text-zinc-400">{s.content}</span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      {source && <SourceDialog workspaceId={workspaceId} citation={source.citation} label={source.label} note={source.note} onClose={() => setSource(null)} />}
      {confirmDelete && (
        <ConfirmDialog
          title="Delete research run?"
          message="The plan, tool activity, gathered sources and report of this run will be permanently deleted. Your documents are not affected."
          confirmLabel="Delete run"
          onClose={() => setConfirmDelete(false)}
          onConfirm={async () => {
            await researchApi.remove(workspaceId, runId);
            setConfirmDelete(false);
            onDeleted();
          }}
        />
      )}
    </div>
  );
}

function StepCard({ step, active }: { step: ResearchStep; active: boolean }) {
  return (
    <li className="rounded-xl border border-zinc-200 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-sm font-medium text-zinc-900 dark:text-zinc-100">
            <span className="mr-1.5 text-zinc-400 tabular-nums">{step.ordinal}.</span>{step.title}
          </p>
          <p className="mt-0.5 text-xs text-zinc-500 dark:text-zinc-400">{step.goal}</p>
          {step.plannedQueries.length > 0 && (
            <div className="mt-2 flex flex-wrap gap-1">
              {step.plannedQueries.map((q) => (
                <span key={q} className="rounded bg-zinc-100 px-1.5 py-0.5 text-[11px] text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300">{q}</span>
              ))}
            </div>
          )}
        </div>
        <StepStatusBadge status={step.status} active={active} />
      </div>
      {step.toolCalls.length > 0 && (
        <ol className="mt-3 space-y-1.5 border-l-2 border-zinc-100 pl-3 dark:border-zinc-800" aria-label={`Tool calls of step ${step.ordinal}`}>
          {step.toolCalls.map((call) => <ToolCallRow key={call.seq} call={call} />)}
        </ol>
      )}
      {step.statusDetail && step.status !== "RUNNING" && <p className="mt-2 text-xs text-zinc-400">{step.statusDetail}</p>}
    </li>
  );
}

/** Arguments are shown as data (parsed JSON), never interpreted. */
function describeArguments(json: string): string {
  try {
    const args = JSON.parse(json) as Record<string, unknown>;
    if (typeof args.query === "string") return `“${args.query}”${typeof args.limit === "number" ? ` (limit ${args.limit})` : ""}`;
    if (typeof args.passage === "string") return `${args.passage}${typeof args.context === "number" ? ` ±${args.context}` : ""}`;
    const text = JSON.stringify(args);
    return text === "{}" ? "" : text;
  } catch {
    return json;
  }
}

function ToolCallRow({ call }: { call: ResearchToolCall }) {
  return (
    <li className="text-xs">
      <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
        <span className="font-mono text-zinc-400 tabular-nums">#{call.seq}</span>
        <span className="font-mono font-medium text-zinc-800 dark:text-zinc-200">{call.tool}</span>
        <span className="break-all text-zinc-600 dark:text-zinc-300">{describeArguments(call.arguments)}</span>
        <ToolCallStatusText status={call.status} />
        {call.durationMs > 0 && <span className="text-zinc-400 tabular-nums">{call.durationMs} ms</span>}
      </div>
      {(call.resultSummary || call.error) && (
        <p className={`mt-0.5 ${call.error ? "text-red-600 dark:text-red-400" : "text-zinc-500 dark:text-zinc-400"}`}>
          → {call.error ?? call.resultSummary}
        </p>
      )}
      {call.reason && <p className="mt-0.5 text-zinc-400 italic">{call.reason}</p>}
    </li>
  );
}
