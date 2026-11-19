import type { Page } from "@/lib/api/types";
import { ChevronLeftIcon, ChevronRightIcon } from "./icons";
import { Button } from "./Button";

export function Pagination({
  page,
  noun,
  onPageChange,
}: {
  page: Page<unknown>;
  noun: string;
  onPageChange: (page: number) => void;
}) {
  if (page.totalItems === 0) return null;
  const from = page.page * page.size + 1;
  const to = page.page * page.size + page.items.length;

  return (
    <nav
      aria-label={`${noun} pagination`}
      className="flex items-center justify-between gap-3 pt-4 text-sm text-zinc-500 dark:text-zinc-400"
    >
      <p>
        <span className="font-medium text-zinc-700 dark:text-zinc-300">{from}–{to}</span> of{" "}
        <span className="font-medium text-zinc-700 dark:text-zinc-300">{page.totalItems}</span>{" "}
        {page.totalItems === 1 ? noun.replace(/s$/, "") : noun}
      </p>
      {page.totalPages > 1 && (
        <div className="flex items-center gap-2">
          <Button
            variant="secondary"
            size="sm"
            icon={<ChevronLeftIcon />}
            disabled={page.page === 0}
            onClick={() => onPageChange(page.page - 1)}
            aria-label="Previous page"
          >
            <span className="hidden sm:inline">Previous</span>
          </Button>
          <span className="tabular-nums">
            {page.page + 1} / {page.totalPages}
          </span>
          <Button
            variant="secondary"
            size="sm"
            disabled={page.page + 1 >= page.totalPages}
            onClick={() => onPageChange(page.page + 1)}
            aria-label="Next page"
          >
            <span className="hidden sm:inline">Next</span>
            <ChevronRightIcon />
          </Button>
        </div>
      )}
    </nav>
  );
}
