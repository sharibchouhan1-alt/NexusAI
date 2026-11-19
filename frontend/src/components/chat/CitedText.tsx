"use client";

import { Fragment, type ReactNode } from "react";
import type { Citation } from "@/lib/api/types";

const MARKER = /\[(\d+)\]/g;

/** Plain text with [n] markers turned into buttons. Never rendered as HTML. */
export function CitedLine({ text, citations, onCite }: { text: string; citations: Citation[]; onCite: (c: Citation) => void }) {
  const byOrdinal = new Map(citations.map((c) => [c.ordinal, c]));
  const parts: ReactNode[] = [];
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
  return <>{parts}</>;
}

/** A chat answer: one block of cited text. */
export function CitedText({ text, citations, onCite }: { text: string; citations: Citation[]; onCite: (c: Citation) => void }) {
  return (
    <p className="leading-relaxed break-words whitespace-pre-wrap">
      <CitedLine text={text} citations={citations} onCite={onCite} />
    </p>
  );
}

/**
 * A report: paragraphs, with lines starting "## " rendered as section headings. Paragraphs
 * without any verified citation are marked, so readers can tell grounded text from synthesis.
 */
export function CitedDocument({ text, citations, onCite }: { text: string; citations: Citation[]; onCite: (c: Citation) => void }) {
  const blocks = text.split(/\n{2,}/);
  const verified = new Set(citations.map((c) => c.ordinal));
  return (
    <div className="space-y-3">
      {blocks.map((block, i) => {
        const lines = block.split("\n");
        const heading = lines[0].startsWith("## ") ? lines[0].slice(3).trim() : null;
        const body = (heading ? lines.slice(1) : lines).join("\n").trim();
        const cited = [...body.matchAll(MARKER)].some((m) => verified.has(Number(m[1])));
        return (
          <section key={i}>
            {heading && <h3 className="mb-1 text-sm font-semibold text-zinc-900 dark:text-zinc-100">{heading}</h3>}
            {body && (
              <p className="text-sm leading-relaxed break-words whitespace-pre-wrap text-zinc-700 dark:text-zinc-300">
                <CitedLine text={body} citations={citations} onCite={onCite} />
                {!cited && (
                  <span
                    className="ml-1.5 inline-flex rounded bg-zinc-100 px-1.5 py-0.5 align-middle text-[10px] font-medium tracking-wide text-zinc-500 uppercase dark:bg-zinc-800 dark:text-zinc-400"
                    title="This paragraph has no verified citation: treat it as the model's synthesis, not as evidence."
                  >
                    uncited
                  </span>
                )}
              </p>
            )}
          </section>
        );
      })}
    </div>
  );
}
