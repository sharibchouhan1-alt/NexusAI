"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useState } from "react";
import { workspacesApi } from "@/lib/api/workspaces";
import { formatDate } from "@/lib/format";
import { useResource } from "@/lib/hooks/useResource";
import { Button } from "@/components/ui/Button";
import { FolderIcon, PlusIcon } from "@/components/ui/icons";
import { Pagination } from "@/components/ui/Pagination";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/states";
import { PageHeader } from "@/components/layout/PageHeader";
import { useWorkspaceNav } from "./WorkspaceNavProvider";
import { WorkspaceFormDialog } from "./WorkspaceFormDialog";

const PAGE_SIZE = 12;

export function WorkspaceDashboard() {
  const router = useRouter();
  const nav = useWorkspaceNav();
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);

  const loader = useCallback(
    (signal: AbortSignal) => workspacesApi.list(page, PAGE_SIZE, signal),
    [page],
  );
  const workspaces = useResource(loader);

  const newButton = (
    <Button icon={<PlusIcon />} onClick={() => setCreating(true)}>
      New workspace
    </Button>
  );

  return (
    <>
      <PageHeader
        title="Workspaces"
        description="Each workspace is an isolated collection of knowledge sources."
        actions={newButton}
      />

      {workspaces.isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" role="status" aria-label="Loading workspaces">
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-32" />
          ))}
        </div>
      ) : workspaces.error ? (
        <ErrorState title="Couldn't load workspaces" error={workspaces.error} onRetry={workspaces.reload} />
      ) : workspaces.data && workspaces.data.totalItems === 0 ? (
        <EmptyState
          icon={<FolderIcon className="size-5" />}
          title="No workspaces yet"
          description="Create your first workspace to start organizing knowledge sources."
          action={newButton}
        />
      ) : workspaces.data && workspaces.data.items.length === 0 ? (
        <EmptyState
          title="This page is empty"
          action={<Button variant="secondary" onClick={() => setPage(0)}>Go to first page</Button>}
        />
      ) : workspaces.data ? (
        <>
          <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {workspaces.data.items.map((workspace) => (
              <li key={workspace.id}>
                <Link
                  href={`/workspaces/${workspace.id}`}
                  className="group flex h-full flex-col rounded-xl border border-zinc-200 bg-white p-5 shadow-xs transition hover:border-indigo-300 hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 dark:border-zinc-800 dark:bg-zinc-900 dark:hover:border-indigo-500/50"
                >
                  <div className="flex items-center gap-3">
                    <span className="flex size-9 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600 dark:bg-indigo-500/10 dark:text-indigo-400">
                      <FolderIcon />
                    </span>
                    <h2 className="min-w-0 truncate font-semibold text-zinc-900 group-hover:text-indigo-600 dark:text-zinc-100 dark:group-hover:text-indigo-400">
                      {workspace.name}
                    </h2>
                  </div>
                  <p className="mt-3 line-clamp-2 flex-1 text-sm text-zinc-500 dark:text-zinc-400">
                    {workspace.description ?? <span className="italic">No description</span>}
                  </p>
                  <p className="mt-4 text-xs text-zinc-400">Created {formatDate(workspace.createdAt)}</p>
                </Link>
              </li>
            ))}
          </ul>
          <Pagination page={workspaces.data} noun="workspaces" onPageChange={setPage} />
        </>
      ) : null}

      {creating && (
        <WorkspaceFormDialog
          onClose={() => setCreating(false)}
          onSaved={(workspace) => {
            setCreating(false);
            workspaces.reload();
            nav.reload();
            router.push(`/workspaces/${workspace.id}`);
          }}
        />
      )}
    </>
  );
}
