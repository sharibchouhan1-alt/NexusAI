"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import type { Citation, Uuid } from "@/lib/api/types";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";

/** Builds a whitespace/case-tolerant pattern for the quote (mirrors the server's verification). */
function quotePattern(quote: string): RegExp | null {
  const words = quote
    .replace(/^["'“”‘’«»\s]+|["'“”‘’«»\s]+$/g, "")
    .split(/\.{3,}|…/)[0]
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((w) => w.replace(/[.*+?^${}()|[\]\\]/g, "\\$&").replace(/["“”]/g, "[\"“”]").replace(/['‘’]/g, "['‘’]"));
  return words.length ? new RegExp(words.join("\\s+"), "i") : null;
}

/** Fallback like the server's: whitespace may appear anywhere (PDF words broken across lines). */
function looseQuotePattern(quote: string): RegExp | null {
  const chars = quote
    .replace(/^["'“”‘’«»\s]+|["'“”‘’«»\s]+$/g, "")
    .split(/\.{3,}|…/)[0]
    .replace(/\s+/g, "")
    .split("")
    .map((c) => c.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"));
  return chars.length ? new RegExp(chars.join("\\s*"), "i") : null;
}

function highlight(passage: string, quote: string): ReactNode {
  const match = quotePattern(quote)?.exec(passage) ?? looseQuotePattern(quote)?.exec(passage) ?? null;
  if (!match) return passage;
  const start = match.index;
  const end = start + match[0].length;
  return (
    <>
      {passage.slice(0, start)}
      <mark className="rounded bg-amber-200/70 px-0.5 text-zinc-900 dark:bg-amber-400/30 dark:text-amber-50">
        {passage.slice(start, end)}
      </mark>
      {passage.slice(end)}
    </>
  );
}

/** Shows the exact evidence behind one citation: document, page, chunk and the verified quote in context. */
export function SourceDialog({
  workspaceId,
  citation,
  onClose,
}: {
  workspaceId: Uuid;
  citation: Citation;
  onClose: () => void;
}) {
  return (
    <Modal
      size="lg"
      title={`[${citation.ordinal}] ${citation.documentTitle}`}
      description={
        <span className="flex flex-wrap gap-x-3 gap-y-1">
          {citation.pageNumber !== null && <span>Page {citation.pageNumber}</span>}
          <span>Chunk #{citation.chunkIndex + 1}</span>
          <span className="font-mono">chars {citation.charStart}–{citation.charEnd}</span>
          <span>similarity {citation.score.toFixed(2)}</span>
        </span>
      }
      onClose={onClose}
      footer={
        <>
          <Link
            href={`/workspaces/${workspaceId}`}
            className="inline-flex h-9 items-center rounded-md px-3.5 text-sm font-medium text-indigo-600 hover:bg-indigo-50 dark:text-indigo-400 dark:hover:bg-indigo-500/10"
          >
            Open workspace documents
          </Link>
          <Button variant="secondary" onClick={onClose}>
            Close
          </Button>
        </>
      }
    >
      <div>
        <p className="mb-1.5 text-xs font-medium tracking-wide text-zinc-500 uppercase">Verified quote</p>
        <blockquote className="border-l-2 border-amber-400 pl-3 text-sm text-zinc-800 italic dark:text-zinc-200">
          “{citation.quote}”
        </blockquote>
      </div>
      <div>
        <p className="mb-1.5 text-xs font-medium tracking-wide text-zinc-500 uppercase">Source passage</p>
        <p className="max-h-[45vh] overflow-y-auto rounded-lg bg-zinc-50 p-3 text-sm leading-relaxed break-words whitespace-pre-wrap text-zinc-700 dark:bg-zinc-800/60 dark:text-zinc-300">
          {highlight(citation.passage, citation.quote)}
        </p>
        <p className="mt-1.5 text-xs text-zinc-400">
          Snapshot of the passage the answer was generated from. Document text is treated as evidence, never as instructions.
        </p>
      </div>
    </Modal>
  );
}
