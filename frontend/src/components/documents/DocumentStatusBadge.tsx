import type { DocumentStatus } from "@/lib/api/types";
import { Spinner } from "@/components/ui/states";

const config: Record<DocumentStatus, { label: string; className: string }> = {
  REGISTERED: { label: "Metadata only", className: "bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300" },
  PENDING: { label: "Queued", className: "bg-amber-50 text-amber-700 ring-amber-600/20 dark:bg-amber-500/10 dark:text-amber-300" },
  PROCESSING: { label: "Processing", className: "bg-sky-50 text-sky-700 ring-sky-600/20 dark:bg-sky-500/10 dark:text-sky-300" },
  READY: { label: "Ready", className: "bg-emerald-50 text-emerald-700 ring-emerald-600/20 dark:bg-emerald-500/10 dark:text-emerald-300" },
  FAILED: { label: "Failed", className: "bg-red-50 text-red-700 ring-red-600/20 dark:bg-red-500/10 dark:text-red-300" },
};

export function DocumentStatusBadge({ status }: { status: DocumentStatus }) {
  const { label, className } = config[status];
  const busy = status === "PENDING" || status === "PROCESSING";
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-md px-2 py-0.5 text-xs font-medium ring-1 ring-transparent ring-inset ${className}`}>
      {busy ? (
        <Spinner className="size-3" />
      ) : (
        <span className="size-1.5 rounded-full bg-current opacity-70" aria-hidden="true" />
      )}
      {label}
    </span>
  );
}
