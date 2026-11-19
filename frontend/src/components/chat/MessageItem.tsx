"use client";

import { Fragment, useEffect, useState } from "react";
import type { ChatMessage, Citation } from "@/lib/api/types";
import { Button } from "@/components/ui/Button";
import { AlertIcon, FileIcon, RefreshIcon } from "@/components/ui/icons";
import { Spinner } from "@/components/ui/states";

const MARKER = /\[(\d+)\]/g;

/** Plain-text answer with [n] markers turned into buttons; never rendered as HTML. */
function AnswerText({ text, citations, onCite }: { text: string; citations: Citation[]; onCite: (c: Citation) => void }) {
  const byOrdinal = new Map(citations.map((c) => [c.ordinal, c]));
  const parts: React.ReactNode[] = [];
  let last = 0;
  for (const match of text.matchAll(MARKER)) {
    const citation = byOrdinal.get(Number(match[1]));
    parts.push(<Fragment key={`t${last}`}>{text.slice(last, match.index)}</Fragment>);
    parts.push(
      citation ? (
        <button
          key={`c${match.index}`}
          type="button"
          onClick={() => onCite(citation)}
          aria-label={`Source ${citation.ordinal}: ${citation.documentTitle}${citation.pageNumber !== null ? `, page ${citation.pageNumber}` : ""}`}
          className="mx-0.5 inline-flex h-5 min-w-5 -translate-y-0.5 items-center justify-center rounded bg-indigo-100 px-1 align-middle text-[11px] font-semibold text-indigo-700 hover:bg-indigo-200 focus-visible:outline-2 focus-visible:outline-indigo-600 dark:bg-indigo-500/20 dark:text-indigo-200 dark:hover:bg-indigo-500/30"
        >
          {citation.ordinal}
        </button>
      ) : (
        <Fragment key={`m${match.index}`}>{match[0]}</Fragment>
      ),
    );
    last = (match.index ?? 0) + match[0].length;
  }
  parts.push(<Fragment key="tail">{text.slice(last)}</Fragment>);
  return <p className="leading-relaxed break-words whitespace-pre-wrap">{parts}</p>;
}

function Elapsed({ since }: { since: number }) {
  const [now, setNow] = useState(since);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  return <span className="tabular-nums">{Math.max(0, Math.round((now - since) / 1000))}s</span>;
}

interface MessageItemProps {
  message: ChatMessage;
  /** Local start time of an in-flight answer, for the elapsed counter. */
  pendingSince?: number;
  canRetry: boolean;
  retrying: boolean;
  onRetry: () => void;
  onCite: (citation: Citation) => void;
}

export function MessageItem({ message, pendingSince, canRetry, retrying, onRetry, onCite }: MessageItemProps) {
  if (message.role === "USER") {
    return (
      <li className="flex justify-end">
        <p className="max-w-[85%] rounded-2xl rounded-br-md bg-indigo-600 px-4 py-2.5 text-sm leading-relaxed break-words whitespace-pre-wrap text-white">
          {message.content}
        </p>
      </li>
    );
  }

  return (
    <li className="flex justify-start">
      <div className="w-full max-w-[92%] text-sm text-zinc-800 dark:text-zinc-200">
        {message.status === "PENDING" && (
          <div className="flex items-center gap-2.5 rounded-2xl rounded-bl-md border border-zinc-200 bg-white px-4 py-3 text-zinc-500 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-400" role="status">
            <Spinner className="size-4" />
            <span>
              Searching your documents and writing an answer locally…{" "}
              {pendingSince !== undefined && <Elapsed since={pendingSince} />}
            </span>
          </div>
        )}

        {message.status === "COMPLETE" && (
          <div className="rounded-2xl rounded-bl-md border border-zinc-200 bg-white px-4 py-3 dark:border-zinc-800 dark:bg-zinc-900">
            <AnswerText text={message.content} citations={message.citations} onCite={onCite} />
            {message.citations.length > 0 && (
              <div className="mt-3 border-t border-zinc-100 pt-2.5 dark:border-zinc-800">
                <p className="mb-1.5 text-xs font-medium text-zinc-500">Sources</p>
                <ul className="flex flex-wrap gap-1.5">
                  {message.citations.map((c) => (
                    <li key={c.ordinal}>
                      <button
                        type="button"
                        onClick={() => onCite(c)}
                        className="inline-flex max-w-72 items-center gap-1.5 rounded-md border border-zinc-200 px-2 py-1 text-xs text-zinc-700 hover:border-indigo-300 hover:bg-indigo-50 dark:border-zinc-700 dark:text-zinc-300 dark:hover:border-indigo-500/50 dark:hover:bg-indigo-500/10"
                      >
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
            <Meta message={message} />
          </div>
        )}

        {message.status === "ABSTAINED" && (
          <div className="rounded-2xl rounded-bl-md border border-amber-200 bg-amber-50 px-4 py-3 dark:border-amber-900/60 dark:bg-amber-950/30">
            <p className="font-medium text-amber-900 dark:text-amber-100">{message.content}</p>
            {message.statusDetail && <p className="mt-1 text-xs text-amber-800 dark:text-amber-300">{message.statusDetail}</p>}
            <p className="mt-1 text-xs text-amber-700/80 dark:text-amber-400/80">
              Try rephrasing, or upload a document that covers this topic.
            </p>
            <Meta message={message} />
          </div>
        )}

        {message.status === "FAILED" && (
          <div className="flex flex-col gap-3 rounded-2xl rounded-bl-md border border-red-200 bg-red-50 px-4 py-3 sm:flex-row sm:items-start dark:border-red-900/60 dark:bg-red-950/30" role="alert">
            <AlertIcon className="mt-0.5 size-4 shrink-0 text-red-600" />
            <div className="flex-1">
              <p className="font-medium text-red-900 dark:text-red-100">The answer couldn&apos;t be generated</p>
              <p className="mt-0.5 text-xs text-red-800 dark:text-red-300">{message.statusDetail}</p>
            </div>
            {canRetry && (
              <Button variant="secondary" size="sm" icon={<RefreshIcon />} loading={retrying} onClick={onRetry}>
                Retry
              </Button>
            )}
          </div>
        )}
      </div>
    </li>
  );
}

function Meta({ message }: { message: ChatMessage }) {
  const parts = [
    message.model,
    message.retrievedCount !== null && `${message.retrievedCount} passage${message.retrievedCount === 1 ? "" : "s"} considered`,
    message.latencyMs !== null && message.latencyMs > 0 && `${(message.latencyMs / 1000).toFixed(1)}s`,
  ].filter(Boolean);
  return parts.length ? <p className="mt-2 text-[11px] text-zinc-400">{parts.join(" · ")}</p> : null;
}
