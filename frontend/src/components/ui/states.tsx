import type { ReactNode } from "react";
import type { ApiError } from "@/lib/api/client";
import { AlertIcon, RefreshIcon } from "./icons";

export function Spinner({ className = "size-5" }: { className?: string }) {
  return (
    <svg className={`animate-spin ${className}`} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeOpacity="0.25" strokeWidth="3" />
      <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    </svg>
  );
}

export function LoadingState({ label = "Loading…" }: { label?: string }) {
  return (
    <div role="status" className="flex items-center justify-center gap-3 py-16 text-sm text-zinc-500 dark:text-zinc-400">
      <Spinner />
      {label}
    </div>
  );
}

export function Skeleton({ className = "" }: { className?: string }) {
  return <div className={`animate-pulse rounded-md bg-zinc-200/70 dark:bg-zinc-800 ${className}`} />;
}

export function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-zinc-300 px-6 py-14 text-center dark:border-zinc-700">
      {icon && (
        <div className="mb-4 flex size-12 items-center justify-center rounded-full bg-indigo-50 text-indigo-600 dark:bg-indigo-500/10 dark:text-indigo-400">
          {icon}
        </div>
      )}
      <h3 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">{title}</h3>
      {description && (
        <p className="mt-1 max-w-sm text-sm text-zinc-500 dark:text-zinc-400">{description}</p>
      )}
      {action && <div className="mt-6">{action}</div>}
    </div>
  );
}

export function Alert({ children, title }: { children: ReactNode; title?: string }) {
  return (
    <div
      role="alert"
      className="flex gap-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900/60 dark:bg-red-950/40 dark:text-red-200"
    >
      <AlertIcon className="mt-0.5 size-4 shrink-0" />
      <div>
        {title && <p className="font-medium">{title}</p>}
        <div className={title ? "mt-0.5" : undefined}>{children}</div>
      </div>
    </div>
  );
}

export function ErrorState({
  error,
  title = "Something went wrong",
  onRetry,
}: {
  error: ApiError;
  title?: string;
  onRetry?: () => void;
}) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-red-200 bg-red-50/50 px-6 py-12 text-center dark:border-red-900/60 dark:bg-red-950/20">
      <div className="mb-4 flex size-12 items-center justify-center rounded-full bg-red-100 text-red-600 dark:bg-red-500/10 dark:text-red-400">
        <AlertIcon className="size-5" />
      </div>
      <h3 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">{title}</h3>
      <p className="mt-1 max-w-md text-sm text-zinc-600 dark:text-zinc-400">{error.message}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-5 inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium text-indigo-600 hover:bg-indigo-50 dark:text-indigo-400 dark:hover:bg-indigo-500/10"
        >
          <RefreshIcon /> Try again
        </button>
      )}
    </div>
  );
}
