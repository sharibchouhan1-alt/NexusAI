"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { use, useCallback } from "react";
import { researchApi } from "@/lib/api/research";
import { workspacesApi } from "@/lib/api/workspaces";
import { formatDateTime } from "@/lib/format";
import { useResource } from "@/lib/hooks/useResource";
import { Button } from "@/components/ui/Button";
import { ChevronLeftIcon, PlusIcon } from "@/components/ui/icons";
import { Alert, EmptyState, Skeleton } from "@/components/ui/states";
import { NewResearchForm } from "./NewResearchForm";
import { RunStatusBadge } from "./ResearchBadges";
import { RunView } from "./RunView";

export function ResearchPage({ params }: { params: Promise<{ workspaceId: string }> }) {
  const { workspaceId } = use(params);
  const router = useRouter();
  const pathname = usePathname();
  const selected = useSearchParams().get("r");

  const workspaceLoader = useCallback((signal: AbortSignal) => workspacesApi.get(workspaceId, signal), [workspaceId]);
  const workspace = useResource(workspaceLoader);
  const listLoader = useCallback((signal: AbortSignal) => researchApi.list(workspaceId, 0, 50, signal), [workspaceId]);
  const runs = useResource(listLoader);
  const { reload: reloadRuns } = runs;

  const select = useCallback(
    (id: string | null) => router.replace(id ? `${pathname}?r=${id}` : pathname, { scroll: false }),
    [router, pathname],
  );

  if (workspace.error?.isNotFound || workspace.error?.status === 400) {
    return (
      <EmptyState
        title="Workspace not found"
        description="It may have been deleted, or the link is incorrect."
        action={<Link href="/" className="text-sm font-medium text-indigo-600 hover:underline dark:text-indigo-400">Back to all workspaces</Link>}
      />
    );
  }

  const items = runs.data?.items ?? [];

  return (
    <div>
      <header className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <Link href={`/workspaces/${workspaceId}`} className="inline-flex items-center gap-1 text-sm text-zinc-500 hover:text-zinc-800 dark:text-zinc-400 dark:hover:text-zinc-200">
            <ChevronLeftIcon /> {workspace.data?.name ?? "Workspace"}
          </Link>
          <h1 className="text-xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">Research</h1>
        </div>
        <Button icon={<PlusIcon />} onClick={() => select(null)} disabled={!selected}>New research</Button>
      </header>

      <div className="flex flex-col gap-6 lg:flex-row">
        <aside className={`lg:w-72 lg:shrink-0 ${selected ? "hidden lg:block" : ""}`} aria-label="Research runs">
          {runs.isLoading ? (
            <div className="space-y-2"><Skeleton className="h-16" /><Skeleton className="h-16" /></div>
          ) : runs.error && !runs.data ? (
            <Alert>Couldn&apos;t load research runs. <button type="button" className="underline" onClick={reloadRuns}>Retry</button></Alert>
          ) : items.length === 0 ? (
            <p className="text-sm text-zinc-500 dark:text-zinc-400">No research yet.</p>
          ) : (
            <ul className="space-y-1">
              {items.map((r) => (
                <li key={r.id}>
                  <button type="button" onClick={() => select(r.id)} aria-current={r.id === selected ? "page" : undefined}
                    className={`block w-full rounded-lg px-3 py-2 text-left transition-colors ${r.id === selected ? "bg-indigo-50 dark:bg-indigo-500/10" : "hover:bg-zinc-100 dark:hover:bg-zinc-800/60"}`}>
                    <span className="line-clamp-2 text-sm text-zinc-800 dark:text-zinc-200">{r.reportTitle ?? r.objective}</span>
                    <span className="mt-1 flex items-center justify-between gap-2">
                      <span className="text-xs text-zinc-400">{formatDateTime(r.createdAt)}</span>
                      <RunStatusBadge status={r.status} />
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </aside>

        <section className="min-w-0 flex-1">
          {selected ? (
            <>
              <button type="button" onClick={() => select(null)} className="mb-3 inline-flex items-center gap-1 text-sm text-zinc-500 lg:hidden">
                <ChevronLeftIcon /> All research
              </button>
              <RunView key={selected} workspaceId={workspaceId} runId={selected} onChanged={reloadRuns}
                onDeleted={() => { reloadRuns(); select(null); }} />
            </>
          ) : (
            <NewResearchForm workspaceId={workspaceId} onStarted={(id) => { reloadRuns(); select(id); }} />
          )}
        </section>
      </div>
    </div>
  );
}
