import type { DocumentMetadata } from "@/lib/api/types";
import { Spinner } from "@/components/ui/states";

type Tone = "neutral" | "amber" | "sky" | "emerald" | "red";

const tones: Record<Tone, string> = {
  neutral: "bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300",
  amber: "bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-300",
  sky: "bg-sky-50 text-sky-700 dark:bg-sky-500/10 dark:text-sky-300",
  emerald: "bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300",
  red: "bg-red-50 text-red-700 dark:bg-red-500/10 dark:text-red-300",
};

/** One badge for the whole lifecycle: ingestion first, then search readiness. */
export function describeStatus(d: Pick<DocumentMetadata, "status" | "indexStatus" | "indexDetail">): {
  label: string;
  tone: Tone;
  busy: boolean;
} {
  switch (d.status) {
    case "REGISTERED":
      return { label: "Metadata only", tone: "neutral", busy: false };
    case "PENDING":
      return { label: "Queued", tone: "amber", busy: true };
    case "PROCESSING":
      return { label: "Extracting", tone: "sky", busy: true };
    case "FAILED":
      return { label: "Failed", tone: "red", busy: false };
    case "READY":
      switch (d.indexStatus) {
        case "INDEXED":
          return { label: "Searchable", tone: "emerald", busy: false };
        case "INDEXING":
          return { label: "Indexing", tone: "sky", busy: true };
        case "PENDING":
          return d.indexDetail
            ? { label: "Waiting for Ollama", tone: "amber", busy: false }
            : { label: "Queued for indexing", tone: "amber", busy: true };
        case "FAILED":
          return { label: "Index failed", tone: "red", busy: false };
        default:
          return { label: "Ready", tone: "emerald", busy: false };
      }
  }
}

export function DocumentStatusBadge({ document }: { document: Pick<DocumentMetadata, "status" | "indexStatus" | "indexDetail"> }) {
  const { label, tone, busy } = describeStatus(document);
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-md px-2 py-0.5 text-xs font-medium whitespace-nowrap ${tones[tone]}`}>
      {busy ? (
        <Spinner className="size-3" />
      ) : (
        <span className="size-1.5 rounded-full bg-current opacity-70" aria-hidden="true" />
      )}
      {label}
    </span>
  );
}
