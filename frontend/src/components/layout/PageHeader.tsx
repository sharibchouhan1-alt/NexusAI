import type { ReactNode } from "react";

export function PageHeader({
  title,
  description,
  actions,
  eyebrow,
}: {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  eyebrow?: ReactNode;
}) {
  return (
    <header className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        {eyebrow && <div className="mb-2">{eyebrow}</div>}
        <h1 className="text-2xl font-semibold tracking-tight break-words text-zinc-900 dark:text-zinc-50">
          {title}
        </h1>
        {description && (
          <div className="mt-1.5 max-w-2xl text-sm break-words text-zinc-500 dark:text-zinc-400">
            {description}
          </div>
        )}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap gap-2">{actions}</div>}
    </header>
  );
}
