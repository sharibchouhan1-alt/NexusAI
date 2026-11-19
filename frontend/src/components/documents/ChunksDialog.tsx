"use client";

import { useCallback, useState } from "react";
import { documentsApi } from "@/lib/api/documents";
import type { DocumentMetadata } from "@/lib/api/types";
import { useResource } from "@/lib/hooks/useResource";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { Pagination } from "@/components/ui/Pagination";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/states";

const PAGE_SIZE = 10;

/** Read-only view of the stored chunks and their provenance (page, character range). */
export function ChunksDialog({ document, onClose }: { document: DocumentMetadata; onClose: () => void }) {
  const [page, setPage] = useState(0);
  const loader = useCallback(
    (signal: AbortSignal) => documentsApi.chunks(document.workspaceId, document.id, page, PAGE_SIZE, signal),
    [document.workspaceId, document.id, page],
  );
  const chunks = useResource(loader);

  return (
    <Modal
      size="lg"
      title={document.title}
      description={`Extracted chunks${document.pageCount ? ` from ${document.pageCount} ${document.pageCount === 1 ? "page" : "pages"}` : ""}. Each chunk keeps its page and character range for later citation.`}
      onClose={onClose}
      footer={
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
      }
    >
      {chunks.isLoading ? (
        <div className="space-y-3" role="status" aria-label="Loading chunks">
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} className="h-24" />
          ))}
        </div>
      ) : chunks.error ? (
        <ErrorState title="Couldn't load chunks" error={chunks.error} onRetry={chunks.reload} />
      ) : chunks.data && chunks.data.totalItems === 0 ? (
        <EmptyState title="No chunks" description="This document has no stored chunks." />
      ) : chunks.data ? (
        <>
          <ol className="max-h-[55vh] space-y-3 overflow-y-auto pr-1">
            {chunks.data.items.map((chunk) => (
              <li key={chunk.id} className="rounded-lg border border-zinc-200 p-3 dark:border-zinc-800">
                <div className="mb-2 flex flex-wrap items-center gap-2 text-xs text-zinc-500 dark:text-zinc-400">
                  <span className="font-medium text-zinc-700 dark:text-zinc-300">#{chunk.index + 1}</span>
                  {chunk.pageNumber !== null && (
                    <span className="rounded bg-indigo-50 px-1.5 py-0.5 font-medium text-indigo-700 dark:bg-indigo-500/10 dark:text-indigo-300">
                      Page {chunk.pageNumber}
                    </span>
                  )}
                  <span className="font-mono tabular-nums">
                    chars {chunk.charStart}–{chunk.charEnd}
                  </span>
                </div>
                <p className="text-sm leading-relaxed break-words whitespace-pre-wrap text-zinc-800 dark:text-zinc-200">
                  {chunk.content}
                </p>
              </li>
            ))}
          </ol>
          <Pagination page={chunks.data} noun="chunks" onPageChange={setPage} />
        </>
      ) : null}
    </Modal>
  );
}
