"use client";

import { useCallback, useState } from "react";
import { documentsApi } from "@/lib/api/documents";
import type { DocumentMetadata, Uuid } from "@/lib/api/types";
import { formatDateTime, safeHttpUrl } from "@/lib/format";
import { useResource } from "@/lib/hooks/useResource";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { FileIcon, LinkIcon, PencilIcon, PlusIcon, TrashIcon } from "@/components/ui/icons";
import { Pagination } from "@/components/ui/Pagination";
import { EmptyState, ErrorState, Skeleton, Spinner } from "@/components/ui/states";
import { DocumentFormDialog } from "./DocumentFormDialog";
import { RenameDocumentDialog } from "./RenameDocumentDialog";

const PAGE_SIZE = 20;

type Dialog =
  | { kind: "create" }
  | { kind: "rename"; document: DocumentMetadata }
  | { kind: "delete"; document: DocumentMetadata }
  | null;

export function DocumentsPanel({ workspaceId }: { workspaceId: Uuid }) {
  const [page, setPage] = useState(0);
  const [dialog, setDialog] = useState<Dialog>(null);

  const loader = useCallback(
    (signal: AbortSignal) => documentsApi.list(workspaceId, page, PAGE_SIZE, signal),
    [workspaceId, page],
  );
  const documents = useResource(loader);
  const data = documents.data;

  const addButton = (
    <Button icon={<PlusIcon />} onClick={() => setDialog({ kind: "create" })}>
      Add document
    </Button>
  );

  return (
    <section aria-labelledby="documents-heading">
      <div className="mb-4 flex items-center justify-between gap-4">
        <div className="flex items-center gap-2">
          <h2 id="documents-heading" className="text-lg font-semibold text-zinc-900 dark:text-zinc-100">
            Documents
          </h2>
          {data && (
            <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-600 tabular-nums dark:bg-zinc-800 dark:text-zinc-300">
              {data.totalItems}
            </span>
          )}
          {documents.isRefreshing && <Spinner className="size-4 text-zinc-400" />}
        </div>
        {data && data.totalItems > 0 && addButton}
      </div>

      {documents.isLoading ? (
        <div className="space-y-2" role="status" aria-label="Loading documents">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="h-14" />
          ))}
        </div>
      ) : documents.error ? (
        <ErrorState title="Couldn't load documents" error={documents.error} onRetry={documents.reload} />
      ) : data && data.totalItems === 0 ? (
        <EmptyState
          icon={<FileIcon className="size-5" />}
          title="No documents yet"
          description="Register the sources this workspace should know about. Upload and ingestion arrive in a later phase."
          action={addButton}
        />
      ) : data && data.items.length === 0 ? (
        <EmptyState
          title="This page is empty"
          action={<Button variant="secondary" onClick={() => setPage(0)}>Go to first page</Button>}
        />
      ) : data ? (
        <>
          <div className="overflow-hidden rounded-xl border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900">
            <table className="w-full table-fixed text-left text-sm">
              <thead className="border-b border-zinc-200 bg-zinc-50 text-xs font-medium tracking-wide text-zinc-500 uppercase dark:border-zinc-800 dark:bg-zinc-900/60 dark:text-zinc-400">
                <tr>
                  <th scope="col" className="px-4 py-3">Title</th>
                  <th scope="col" className="hidden w-28 px-4 py-3 md:table-cell">Type</th>
                  <th scope="col" className="hidden w-44 px-4 py-3 lg:table-cell">Media type</th>
                  <th scope="col" className="hidden w-44 px-4 py-3 md:table-cell">Added</th>
                  <th scope="col" className="w-24 px-4 py-3"><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800">
                {data.items.map((document) => (
                  <DocumentRow
                    key={document.id}
                    document={document}
                    onRename={() => setDialog({ kind: "rename", document })}
                    onDelete={() => setDialog({ kind: "delete", document })}
                  />
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={data} noun="documents" onPageChange={setPage} />
        </>
      ) : null}

      {dialog?.kind === "create" && (
        <DocumentFormDialog
          workspaceId={workspaceId}
          onClose={() => setDialog(null)}
          onCreated={() => {
            setDialog(null);
            // Newest first: show the new document on the first page.
            if (page === 0) documents.reload();
            else setPage(0);
          }}
        />
      )}

      {dialog?.kind === "rename" && (
        <RenameDocumentDialog
          document={dialog.document}
          onClose={() => setDialog(null)}
          onSaved={() => {
            setDialog(null);
            documents.reload();
          }}
        />
      )}

      {dialog?.kind === "delete" && (
        <ConfirmDialog
          title="Delete document?"
          message={
            <>
              <strong className="font-medium text-zinc-700 dark:text-zinc-200">{dialog.document.title}</strong>{" "}
              and its metadata will be permanently removed from this workspace.
            </>
          }
          confirmLabel="Delete document"
          onClose={() => setDialog(null)}
          onConfirm={async () => {
            await documentsApi.remove(workspaceId, dialog.document.id);
            setDialog(null);
            // Step back if this removed the last item on a later page.
            if (data && data.items.length === 1 && page > 0) setPage(page - 1);
            else documents.reload();
          }}
        />
      )}
    </section>
  );
}

function DocumentRow({
  document,
  onRename,
  onDelete,
}: {
  document: DocumentMetadata;
  onRename: () => void;
  onDelete: () => void;
}) {
  const href = document.sourceType === "URL" ? safeHttpUrl(document.origin) : null;
  const Icon = document.sourceType === "URL" ? LinkIcon : FileIcon;

  return (
    <tr className="align-top hover:bg-zinc-50/60 dark:hover:bg-zinc-800/30">
      <td className="px-4 py-3">
        <div className="flex min-w-0 items-start gap-3">
          <span className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-md bg-zinc-100 text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400">
            <Icon />
          </span>
          <div className="min-w-0">
            <p className="truncate font-medium text-zinc-900 dark:text-zinc-100" title={document.title}>
              {document.title}
            </p>
            {document.origin && (
              <p className="truncate text-xs text-zinc-500 dark:text-zinc-400" title={document.origin}>
                {href ? (
                  <a href={href} target="_blank" rel="noopener noreferrer" className="hover:text-indigo-600 hover:underline dark:hover:text-indigo-400">
                    {document.origin}
                  </a>
                ) : (
                  document.origin
                )}
              </p>
            )}
            <p className="mt-1 text-xs text-zinc-400 md:hidden">
              {document.sourceType === "URL" ? "Web link" : "File"} · {formatDateTime(document.createdAt)}
            </p>
          </div>
        </div>
      </td>
      <td className="hidden px-4 py-3 md:table-cell">
        <span className="inline-flex items-center rounded-md bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300">
          {document.sourceType === "URL" ? "Web link" : "File"}
        </span>
      </td>
      <td className="hidden truncate px-4 py-3 font-mono text-xs text-zinc-500 lg:table-cell dark:text-zinc-400">
        {document.mediaType ?? "—"}
      </td>
      <td className="hidden px-4 py-3 text-zinc-500 md:table-cell dark:text-zinc-400">
        {formatDateTime(document.createdAt)}
      </td>
      <td className="px-4 py-2.5">
        <div className="flex justify-end gap-1">
          <Button variant="ghost" size="sm" onClick={onRename} aria-label={`Rename ${document.title}`} icon={<PencilIcon />} />
          <Button
            variant="ghost"
            size="sm"
            onClick={onDelete}
            aria-label={`Delete ${document.title}`}
            icon={<TrashIcon />}
            className="hover:bg-red-50! hover:text-red-600! dark:hover:bg-red-950/40!"
          />
        </div>
      </td>
    </tr>
  );
}
