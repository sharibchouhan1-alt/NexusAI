"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Suspense, useState, type ReactNode } from "react";
import { CloseIcon, FolderIcon, HomeIcon, MenuIcon, PlusIcon } from "@/components/ui/icons";
import { Skeleton } from "@/components/ui/states";
import { WorkspaceFormDialog } from "@/components/workspaces/WorkspaceFormDialog";
import { WorkspaceNavProvider, useWorkspaceNav } from "@/components/workspaces/WorkspaceNavProvider";
import { BackendStatus } from "./BackendStatus";

export function AppShell({ children }: { children: ReactNode }) {
  return (
    <WorkspaceNavProvider>
      <Shell>{children}</Shell>
    </WorkspaceNavProvider>
  );
}

function Shell({ children }: { children: ReactNode }) {
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <div className="flex min-h-dvh">
      {/* Desktop sidebar */}
      <aside className="hidden w-64 shrink-0 border-r border-zinc-200 bg-white lg:block dark:border-zinc-800 dark:bg-zinc-950">
        <div className="sticky top-0 flex h-dvh flex-col">
          <Sidebar />
        </div>
      </aside>

      {/* Mobile drawer */}
      {mobileOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <button
            type="button"
            aria-label="Close navigation"
            className="absolute inset-0 bg-zinc-950/50"
            onClick={() => setMobileOpen(false)}
          />
          <aside className="relative flex h-full w-72 max-w-[85%] flex-col bg-white shadow-xl dark:bg-zinc-950">
            <button
              type="button"
              aria-label="Close navigation"
              onClick={() => setMobileOpen(false)}
              className="absolute top-4 right-3 rounded-md p-1.5 text-zinc-500 hover:bg-zinc-100 dark:hover:bg-zinc-800"
            >
              <CloseIcon />
            </button>
            <Sidebar onNavigate={() => setMobileOpen(false)} />
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-zinc-200 bg-white/90 px-4 backdrop-blur lg:hidden dark:border-zinc-800 dark:bg-zinc-950/90">
          <button
            type="button"
            aria-label="Open navigation"
            onClick={() => setMobileOpen(true)}
            className="-ml-1.5 rounded-md p-1.5 text-zinc-600 hover:bg-zinc-100 dark:text-zinc-300 dark:hover:bg-zinc-800"
          >
            <MenuIcon className="size-5" />
          </button>
          <Brand />
        </header>
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 sm:px-6 lg:px-10 lg:py-10">{children}</main>
      </div>
    </div>
  );
}

function Brand() {
  return (
    <Link href="/" className="flex items-center gap-2 font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">
      <span className="flex size-7 items-center justify-center rounded-lg bg-indigo-600 text-xs font-bold text-white">N</span>
      NexusAI
    </Link>
  );
}

/**
 * The current path is request-specific, so it is read inside a Suspense boundary; the
 * fallback is the same sidebar without an active highlight (no layout shift).
 */
function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <Suspense fallback={<SidebarContent pathname={null} onNavigate={onNavigate} />}>
      <SidebarWithPathname onNavigate={onNavigate} />
    </Suspense>
  );
}

function SidebarWithPathname({ onNavigate }: { onNavigate?: () => void }) {
  return <SidebarContent pathname={usePathname()} onNavigate={onNavigate} />;
}

function SidebarContent({ pathname, onNavigate }: { pathname: string | null; onNavigate?: () => void }) {
  const router = useRouter();
  const nav = useWorkspaceNav();
  const [creating, setCreating] = useState(false);

  const itemClass = (active: boolean) =>
    `flex items-center gap-2.5 rounded-md px-2.5 py-1.5 text-sm transition-colors ${
      active
        ? "bg-indigo-50 font-medium text-indigo-700 dark:bg-indigo-500/10 dark:text-indigo-300"
        : "text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900 dark:text-zinc-400 dark:hover:bg-zinc-800/60 dark:hover:text-zinc-100"
    }`;

  return (
    <>
      <div className="flex h-16 items-center px-5">
        <Brand />
      </div>

      <nav aria-label="Main" className="flex min-h-0 flex-1 flex-col gap-6 overflow-y-auto px-3 pb-4">
        <Link href="/" onClick={onNavigate} className={itemClass(pathname === "/")} aria-current={pathname === "/" ? "page" : undefined}>
          <HomeIcon /> Dashboard
        </Link>

        <div>
          <div className="mb-1 flex items-center justify-between px-2.5">
            <h2 className="text-xs font-semibold tracking-wide text-zinc-400 uppercase">Workspaces</h2>
            <button
              type="button"
              onClick={() => setCreating(true)}
              aria-label="New workspace"
              className="rounded p-1 text-zinc-400 hover:bg-zinc-100 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200"
            >
              <PlusIcon />
            </button>
          </div>

          {nav.isLoading ? (
            <div className="space-y-1.5 px-2.5 pt-1">
              <Skeleton className="h-5" />
              <Skeleton className="h-5 w-4/5" />
              <Skeleton className="h-5 w-3/5" />
            </div>
          ) : nav.error ? (
            <p className="px-2.5 py-1 text-xs text-red-600 dark:text-red-400">
              Couldn&apos;t load workspaces.{" "}
              <button type="button" onClick={nav.reload} className="underline">Retry</button>
            </p>
          ) : nav.data && nav.data.items.length === 0 ? (
            <p className="px-2.5 py-1 text-xs text-zinc-400">No workspaces yet.</p>
          ) : (
            <ul className="space-y-0.5">
              {nav.data?.items.map((workspace) => {
                const href = `/workspaces/${workspace.id}`;
                const active = pathname === href;
                return (
                  <li key={workspace.id}>
                    <Link href={href} onClick={onNavigate} className={itemClass(active)} aria-current={active ? "page" : undefined}>
                      <FolderIcon />
                      <span className="truncate">{workspace.name}</span>
                    </Link>
                  </li>
                );
              })}
              {nav.data && nav.data.totalItems > nav.data.items.length && (
                <li>
                  <Link href="/" onClick={onNavigate} className="block px-2.5 py-1.5 text-xs text-zinc-500 hover:text-indigo-600">
                    View all {nav.data.totalItems} on the dashboard
                  </Link>
                </li>
              )}
            </ul>
          )}
        </div>
      </nav>

      <div className="border-t border-zinc-200 px-5 py-4 dark:border-zinc-800">
        <BackendStatus />
      </div>

      {creating && (
        <WorkspaceFormDialog
          onClose={() => setCreating(false)}
          onSaved={(workspace) => {
            setCreating(false);
            nav.reload();
            onNavigate?.();
            router.push(`/workspaces/${workspace.id}`);
          }}
        />
      )}
    </>
  );
}
