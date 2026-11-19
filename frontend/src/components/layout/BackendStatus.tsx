"use client";

import { useEffect, useState } from "react";
import { healthApi } from "@/lib/api/health";
import type { HealthReport } from "@/lib/api/types";

type Status = "checking" | "up" | "degraded" | "offline";

const POLL_MS = 30_000;

const styles: Record<Status, { dot: string; label: string }> = {
  checking: { dot: "bg-zinc-400", label: "Checking backend…" },
  up: { dot: "bg-emerald-500", label: "Backend online" },
  degraded: { dot: "bg-amber-500", label: "Database unavailable" },
  offline: { dot: "bg-red-500", label: "Backend offline" },
};

/** Live indicator driven by GET /api/health. */
export function BackendStatus() {
  const [status, setStatus] = useState<Status>("checking");
  const [pgvector, setPgvector] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    const check = () =>
      healthApi.get(controller.signal).then(
        (report: HealthReport) => {
          setStatus("up");
          setPgvector(report.database.pgvector);
        },
        (error: { status?: number }) => {
          if (controller.signal.aborted) return;
          // 503 carries a DOWN report: the API answered but the database did not.
          setStatus(error.status === 503 ? "degraded" : "offline");
        },
      );
    check();
    const timer = setInterval(check, POLL_MS);
    return () => {
      clearInterval(timer);
      controller.abort();
    };
  }, []);

  const { dot, label } = styles[status];
  return (
    <div className="flex items-center gap-2 text-xs text-zinc-500 dark:text-zinc-400" role="status">
      <span className={`size-2 rounded-full ${dot}`} aria-hidden="true" />
      <span>{label}</span>
      {status === "up" && pgvector && <span className="text-zinc-400">· pgvector {pgvector}</span>}
    </div>
  );
}
