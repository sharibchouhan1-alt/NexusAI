"use client";

import { useCallback, useEffect, useState } from "react";
import { toApiError } from "@/lib/api/client";
import { documentsApi } from "@/lib/api/documents";
import { IN_FLIGHT_STATUSES, type DocumentMetadata, type Uuid } from "@/lib/api/types";
import { formatBytes, formatDateTime, safeHttpUrl } from "@/lib/format";
import { useResource } from "@/lib/hooks/useResource";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { FileIcon, LinkIcon, PencilIcon, PlusIcon, RefreshIcon, TrashIcon } from "@/components/ui/icons";
import { Pagination } from "@/components/ui/Pagination";
import { Alert, EmptyState, ErrorState, Skeleton, Spinner } from "@/components/ui/states";
import { ChunksDialog } from "./ChunksDialog";
import { DocumentFormDialog } from "./DocumentFormDialog";
import { DocumentStatusBadge } from "./DocumentStatusBadge";
import { RenameDocumentDialog } from "./RenameDocumentDialog";
import { UploadDialog } from "./UploadDialog";

const PAGE_SIZE = 20;
/** While any listed document is queued or processing, the list refreshes at this interval. */
const POLL_MS = 1500;

type Dialog =
  | { kind: "upload" }
  | { kind: "create" }
  | { kind: "rename"; document: DocumentMetadata }
  | { kind: "delete"; document: DocumentMetadata }
  | { kind: "chunks"; document: DocumentMetadata }
  | null;

export function DocumentsPanel({ workspaceId }: { workspaceId: Uuid }) {
  const [page, setPage] = useState(0);
  const [dialog, setDialog] = useState<Dialog>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const loader = useCallback(
    (signal: AbortSignal) => documentsApi.list(workspaceId, page, PAGE_SIZE, signal),
    [workspaceId, page],
  );
  const documents = useResource(loader);
  const data = documents.data;
  const { reload } = documents;

  // Poll only while ingestion is in flight; stops by itself once everything has settled.
  const inFlight = data?.items.some((d) => IN_FLIGHT_STATUSES.includes(d.status)) ?? false;
  useEffect(() => {
    if (!inFlight) return;
    const timer = setTimeout(reload, POLL_MS);
    return () => clearTimeout(timer);
  }, [inFlight, data, reload]);

  async function retry(document: DocumentMetadata) {
    setActionError(null);
    try {
      await documentsApi.reingest(workspaceId, document.id);
    } catch (error) {
      setActionError(`Couldn't retry "${document.title}": ${toApiError(error).message}`);
    }
    reload();
  }

  const actions = (
    <div className="flex gap-2">
      <Button variant="secondary" icon={<LinkIcon />} onClick={() => setDialog({ kind: "create" })}>
        Add metadata
      </Button>
      <Button icon={<PlusIcon />} onClick={() => setDialog({ kind: "upload" })}>
        Upload file
      </Button>
    </div>
  );

  return (
    <section aria-labelledby="documents-heading">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <h2 id="documents-heading" className="text-lg font-semibold text-zinc-900 dark:text-zinc-100">
            Documents
          </h2>
          {data && (
            <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-600 tabular-nums dark:bg-zinc-800 dark:text-zinc-300">
              {data.totalItems}
            </span>
          )}
          {(documents.isRefreshing || inFlight) && (
            <span className="flex items-center gap-1.5 text-xs text-zinc-400" role="status">
              <Spinner className="size-3.5" />
              {inFlight && "Processing…"}
            </span>
          )}
        </div>
        {data && data.totalItems > 0 && actions}
      </div>

      {actionError && (
        <div className="mb-4">
          <Alert>{actionError}</Alert>
        </div>
      )}

      {documents.isLoading ? (
        <div className="space-y-2" role="status" aria-label="Loading documents">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="h-14" />
          ))}
        </div>
      ) : documents.error ? (
        <ErrorState title="Couldn't load documents" error={documents.error} onRetry={reload} />
      ) : data && data.totalItems === 0 ? (
        <EmptyState
          icon={<FileIcon className="size-5" />}
          title="No documents yet"
          description="Upload a PDF or text file to extract and chunk its content, or register a web source's metadata."
          action={actions}
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
                  <th scope="col" className="hidden w-36 px-4 py-3 sm:table-cell">Status</th>
                  <th scope="col" className="hidden w-52 px-4 py-3 lg:table-cell">Details</th>
                  <th scope="col" className="hidden w-44 px-4 py-3 md:table-cell">Added</th>
                  <th scope="col" className="w-28 px-4 py-3"><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800">
                {data.items.map((document) => (
                  <DocumentRow
                    key={document.id}
                    document={document}
                    onRename={() => setDialog({ kind: "rename", document })}
                    onDelete={() => setDialog({ kind: "delete", document })}
                    onViewChunks={() => setDialog({ kind: "chunks", document })}
                    onRetry={() => retry(document)}
                  />
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={data} noun="documents" onPageChange={setPage} />
        </>
      ) : null}

      {dialog?.kind === "upload" && (
        <UploadDialog
          workspaceId={workspaceId}
          onClose={() => setDialog(null)}
          onUploaded={() => {
            setDialog(null);
            if (page === 0) reload();
            else setPage(0);
          }}
        />
      )}

      {dialog?.kind === "create" && (
        <DocumentFormDialog
          workspaceId={workspaceId}
          onClose={() => setDialog(null)}
          onCreated={() => {
            setDialog(null);
            // Newest first: show the new document on the first page.
            if (page === 0) reload();
            else setPage(0);
          }}
        />
      )}

      {dialog?.kind === "chunks" && <ChunksDialog document={dialog.document} onClose={() => setDialog(null)} />}

      {dialog?.kind === "rename" && (
        <RenameDocumentDialog
          document={dialog.document}
          onClose={() => setDialog(null)}
          onSaved={() => {
            setDialog(null);
            reload();
          }}
        />
      )}

      {dialog?.kind === "delete" && (
        <ConfirmDialog
          title="Delete document?"
          message={
            <>
              <strong className="font-medium text-zinc-700 dark:text-zinc-200">{dialog.document.title}</strong>{" "}
              {dialog.document.fileSizeBytes !== null
                ? "and its uploaded file and extracted chunks will be permanently removed."
                : "and its metadata will be permanently removed from this workspace."}
            </>
          }
          confirmLabel="Delete document"
          onClose={() => setDialog(null)}
          onConfirm={async () => {
            await documentsApi.remove(workspaceId, dialog.document.id);
            setDialog(null);
            // Step back if this removed the last item on a later page.
            if (data && data.items.length === 1 && page > 0) setPage(page - 1);
            else reload();
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
  onViewChunks,
  onRetry,
}: {
  document: DocumentMetadata;
  onRename: () => void;
  onDelete: () => void;
  onViewChunks: () => void;
  onRetry: () => void;
}) {
  const href = document.sourceType === "URL" ? safeHttpUrl(document.origin) : null;
  const Icon = document.sourceType === "URL" ? LinkIcon : FileIcon;
  const details = [
    document.mediaType === "application/pdf" ? "PDF" : document.mediaType === "text/plain" ? "Text" : document.mediaType,
    document.fileSizeBytes !== null ? formatBytes(document.fileSizeBytes) : null,
    document.pageCount !== null ? `${document.pageCount} ${document.pageCount === 1 ? "page" : "pages"}` : null,
    document.chunkCount !== null ? `${document.chunkCount} ${document.chunkCount === 1 ? "chunk" : "chunks"}` : null,
  ].filter(Boolean);

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
            {document.status === "FAILED" && document.statusDetail && (
              <p className="mt-1 text-xs text-red-600 dark:text-red-400">{document.statusDetail}</p>
            )}
            <div className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-zinc-400 sm:hidden">
              <DocumentStatusBadge status={document.status} />
              {details.join(" · ")}
            </div>
          </div>
        </div>
      </td>
      <td className="hidden px-4 py-3 sm:table-cell">
        <DocumentStatusBadge status={document.status} />
      </td>
      <td className="hidden px-4 py-3 text-xs text-zinc-500 lg:table-cell dark:text-zinc-400">
        {details.length > 0 ? details.join(" · ") : "—"}
      </td>
      <td className="hidden px-4 py-3 text-zinc-500 md:table-cell dark:text-zinc-400">
        {formatDateTime(document.createdAt)}
      </td>
      <td className="px-3 py-2.5">
        <div className="flex justify-end gap-0.5">
          {document.status === "READY" && (
            <Button variant="ghost" size="sm" onClick={onViewChunks} aria-label={`View chunks of ${document.title}`} title="View chunks" icon={<FileIcon />} />
          )}
          {document.status === "FAILED" && (
            <Button variant="ghost" size="sm" onClick={onRetry} aria-label={`Retry processing ${document.title}`} title="Retry processing" icon={<RefreshIcon />} />
          )}
          <Button variant="ghost" size="sm" onClick={onRename} aria-label={`Rename ${document.title}`} title="Rename" icon={<PencilIcon />} />
          <Button
            variant="ghost"
            size="sm"
            onClick={onDelete}
            aria-label={`Delete ${document.title}`}
            title="Delete"
            icon={<TrashIcon />}
            className="hover:bg-red-50! hover:text-red-600! dark:hover:bg-red-950/40!"
          />
        </div>
      </td>
    </tr>
  );
}
