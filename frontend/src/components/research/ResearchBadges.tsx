import type { ResearchStatus, ResearchStepStatus, ToolCallStatus } from "@/lib/api/types";
import { Spinner } from "@/components/ui/states";

type Tone = "neutral" | "sky" | "emerald" | "amber" | "red";

const tones: Record<Tone, string> = {
  neutral: "bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300",
  sky: "bg-sky-50 text-sky-700 dark:bg-sky-500/10 dark:text-sky-300",
  emerald: "bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300",
  amber: "bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-300",
  red: "bg-red-50 text-red-700 dark:bg-red-500/10 dark:text-red-300",
};

const runConfig: Record<ResearchStatus, { label: string; tone: Tone; busy: boolean }> = {
  QUEUED: { label: "Queued", tone: "neutral", busy: true },
  PLANNING: { label: "Planning", tone: "sky", busy: true },
  RUNNING: { label: "Researching", tone: "sky", busy: true },
  SYNTHESIZING: { label: "Writing report", tone: "sky", busy: true },
  COMPLETED: { label: "Completed", tone: "emerald", busy: false },
  INSUFFICIENT_EVIDENCE: { label: "Insufficient evidence", tone: "amber", busy: false },
  FAILED: { label: "Failed", tone: "red", busy: false },
  CANCELLED: { label: "Cancelled", tone: "neutral", busy: false },
};

function Badge({ label, tone, busy }: { label: string; tone: Tone; busy: boolean }) {
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-md px-2 py-0.5 text-xs font-medium whitespace-nowrap ${tones[tone]}`}>
      {busy ? <Spinner className="size-3" /> : <span className="size-1.5 rounded-full bg-current opacity-70" aria-hidden="true" />}
      {label}
    </span>
  );
}

export function RunStatusBadge({ status }: { status: ResearchStatus }) {
  return <Badge {...runConfig[status]} />;
}

const stepConfig: Record<ResearchStepStatus, { label: string; tone: Tone; busy: boolean }> = {
  PENDING: { label: "Pending", tone: "neutral", busy: false },
  RUNNING: { label: "Running", tone: "sky", busy: true },
  COMPLETED: { label: "Done", tone: "emerald", busy: false },
  FAILED: { label: "Failed", tone: "red", busy: false },
  SKIPPED: { label: "Skipped", tone: "neutral", busy: false },
};

export function StepStatusBadge({ status, active }: { status: ResearchStepStatus; active: boolean }) {
  const config = stepConfig[status];
  // A RUNNING step of a stopped run was interrupted; don't show a spinner for it.
  return <Badge {...config} busy={config.busy && active} label={status === "RUNNING" && !active ? "Interrupted" : config.label} />;
}

const callTone: Record<ToolCallStatus, string> = {
  SUCCEEDED: "text-emerald-600 dark:text-emerald-400",
  FAILED: "text-red-600 dark:text-red-400",
  REJECTED: "text-amber-600 dark:text-amber-400",
};

export function ToolCallStatusText({ status }: { status: ToolCallStatus }) {
  return <span className={`text-[11px] font-semibold tracking-wide uppercase ${callTone[status]}`}>{status.toLowerCase()}</span>;
}
