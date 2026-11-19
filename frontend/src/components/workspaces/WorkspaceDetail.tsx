"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { use, useCallback, useState } from "react";
import { toApiError } from "@/lib/api/client";
import { workspacesApi } from "@/lib/api/workspaces";
import { formatDateTime } from "@/lib/format";
import { useResource } from "@/lib/hooks/useResource";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { ChatIcon, ChevronLeftIcon, FolderIcon, PencilIcon, SearchIcon, TrashIcon } from "@/components/ui/icons";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/states";
import { PageHeader } from "@/components/layout/PageHeader";
import { DocumentsPanel } from "@/components/documents/DocumentsPanel";
import { SearchPanel } from "@/components/search/SearchPanel";
import { useWorkspaceNav } from "./WorkspaceNavProvider";
import { WorkspaceFormDialog } from "./WorkspaceFormDialog";

type Dialog = "edit" | "delete" | null;

export function WorkspaceDetail({ params }: { params: Promise<{ workspaceId: string }> }) {
  const { workspaceId } = use(params);
  const router = useRouter();
  const nav = useWorkspaceNav();
  const [dialog, setDialog] = useState<Dialog>(null);

  const loader = useCallback(
    (signal: AbortSignal) => workspacesApi.get(workspaceId, signal),
    [workspaceId],
  );
  const { data: workspace, error, isLoading, reload } = useResource(loader);

  if (isLoading) return <LoadingState label="Loading workspace…" />;

  if (error) {
    // 400 = malformed id in the URL; treat it like any missing workspace.
    if (error.isNotFound || error.status === 400) {
      return (
        <EmptyState
          icon={<FolderIcon className="size-5" />}
          title="Workspace not found"
          description="It may have been deleted, or the link is incorrect."
          action={
            <Link href="/" className="text-sm font-medium text-indigo-600 hover:underline dark:text-indigo-400">
              Back to all workspaces
            </Link>
          }
        />
      );
    }
    return <ErrorState title="Couldn't load this workspace" error={error} onRetry={reload} />;
  }

  if (!workspace) return null;

  return (
    <>
      <PageHeader
        eyebrow={
          <Link
            href="/"
            className="inline-flex items-center gap-1 text-sm text-zinc-500 hover:text-zinc-800 dark:text-zinc-400 dark:hover:text-zinc-200"
          >
            <ChevronLeftIcon /> All workspaces
          </Link>
        }
        title={workspace.name}
        description={
          <>
            {workspace.description && <p className="mb-1">{workspace.description}</p>}
            <p className="text-xs text-zinc-400">
              Created {formatDateTime(workspace.createdAt)} · Updated {formatDateTime(workspace.updatedAt)}
            </p>
          </>
        }
        actions={
          <>
            <Link
              href={`/workspaces/${workspace.id}/chat`}
              className="inline-flex h-9 items-center gap-2 rounded-md bg-indigo-600 px-3.5 text-sm font-medium text-white shadow-sm hover:bg-indigo-500 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 dark:bg-indigo-500 dark:hover:bg-indigo-400"
            >
              <ChatIcon /> Chat
            </Link>
            <Link
              href={`/workspaces/${workspace.id}/research`}
              className="inline-flex h-9 items-center gap-2 rounded-md bg-white px-3.5 text-sm font-medium text-zinc-900 shadow-sm ring-1 ring-zinc-300 ring-inset hover:bg-zinc-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 dark:bg-zinc-800 dark:text-zinc-100 dark:ring-zinc-700 dark:hover:bg-zinc-700"
            >
              <SearchIcon /> Research
            </Link>
            <Button variant="secondary" icon={<PencilIcon />} onClick={() => setDialog("edit")}>
              Edit
            </Button>
            <Button variant="secondary" icon={<TrashIcon />} onClick={() => setDialog("delete")}>
              Delete
            </Button>
          </>
        }
      />

      {/* Keyed by workspace: local state (query, results, page) must not carry over between workspaces. */}
      <SearchPanel key={`search-${workspace.id}`} workspaceId={workspace.id} />
      <DocumentsPanel key={`documents-${workspace.id}`} workspaceId={workspace.id} />

      {dialog === "edit" && (
        <WorkspaceFormDialog
          workspace={workspace}
          onClose={() => setDialog(null)}
          onSaved={() => {
            setDialog(null);
            reload();
            nav.reload();
          }}
        />
      )}

      {dialog === "delete" && (
        <ConfirmDialog
          title="Delete workspace?"
          message={
            <>
              <strong className="font-medium text-zinc-700 dark:text-zinc-200">{workspace.name}</strong>{" "}
              will be permanently deleted. Only empty workspaces can be deleted.
            </>
          }
          confirmLabel="Delete workspace"
          onClose={() => setDialog(null)}
          onConfirm={async () => {
            await workspacesApi.remove(workspace.id);
            setDialog(null);
            nav.reload();
            router.push("/");
          }}
          describeError={(e) => {
            const apiError = toApiError(e);
            return apiError.status === 409
              ? "This workspace still contains documents. Delete its documents first, then try again."
              : apiError.message;
          }}
        />
      )}
    </>
  );
}
