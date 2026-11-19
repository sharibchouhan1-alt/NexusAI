"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { use, useCallback, useState, type FormEvent } from "react";
import { toApiError } from "@/lib/api/client";
import { chatApi } from "@/lib/api/chat";
import { workspacesApi } from "@/lib/api/workspaces";
import { CHAT_LIMITS, type ConversationSummary } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { useResource } from "@/lib/hooks/useResource";
import { Button } from "@/components/ui/Button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { Modal } from "@/components/ui/Modal";
import { TextField } from "@/components/ui/fields";
import { ChevronLeftIcon, PencilIcon, PlusIcon, TrashIcon } from "@/components/ui/icons";
import { Alert, EmptyState, Skeleton } from "@/components/ui/states";
import { ChatThread } from "./ChatThread";

type Dialog = { kind: "rename"; conversation: ConversationSummary } | { kind: "delete"; conversation: ConversationSummary } | null;

export function ChatPage({ params }: { params: Promise<{ workspaceId: string }> }) {
  const { workspaceId } = use(params);
  const router = useRouter();
  const pathname = usePathname();
  const selected = useSearchParams().get("c");
  const [dialog, setDialog] = useState<Dialog>(null);

  const workspaceLoader = useCallback((signal: AbortSignal) => workspacesApi.get(workspaceId, signal), [workspaceId]);
  const workspace = useResource(workspaceLoader);
  const listLoader = useCallback((signal: AbortSignal) => chatApi.list(workspaceId, 0, 50, signal), [workspaceId]);
  const conversations = useResource(listLoader);

  const select = useCallback(
    (id: string | null) => router.replace(id ? `${pathname}?c=${id}` : pathname, { scroll: false }),
    [router, pathname],
  );

  if (workspace.error?.isNotFound || workspace.error?.status === 400) {
    return (
      <EmptyState
        title="Workspace not found"
        description="It may have been deleted, or the link is incorrect."
        action={<Link href="/" className="text-sm font-medium text-indigo-600 hover:underline dark:text-indigo-400">Back to all workspaces</Link>}
      />
    );
  }

  const items = conversations.data?.items ?? [];

  return (
    <div className="-my-4 flex h-[calc(100dvh-8rem)] min-h-[520px] flex-col lg:-my-4 lg:h-[calc(100dvh-5rem)]">
      <header className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <Link
            href={`/workspaces/${workspaceId}`}
            className="inline-flex items-center gap-1 text-sm text-zinc-500 hover:text-zinc-800 dark:text-zinc-400 dark:hover:text-zinc-200"
          >
            <ChevronLeftIcon /> {workspace.data?.name ?? "Workspace"}
          </Link>
          <h1 className="text-xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">Chat</h1>
        </div>
        <Button icon={<PlusIcon />} onClick={() => select(null)} disabled={!selected}>
          New chat
        </Button>
      </header>

      <div className="flex min-h-0 flex-1 gap-6">
        {/* Conversation list: always on desktop; on mobile only when no conversation is open. */}
        <aside className={`min-h-0 w-full flex-col md:flex md:w-64 md:shrink-0 ${selected ? "hidden" : "flex"}`} aria-label="Conversations">
          {conversations.isLoading ? (
            <div className="space-y-2">
              <Skeleton className="h-12" />
              <Skeleton className="h-12" />
              <Skeleton className="h-12" />
            </div>
          ) : conversations.error && !conversations.data ? (
            <Alert>
              Couldn&apos;t load conversations.{" "}
              <button type="button" className="underline" onClick={conversations.reload}>Retry</button>
            </Alert>
          ) : items.length === 0 ? (
            <p className="px-1 text-sm text-zinc-500 dark:text-zinc-400">No conversations yet. Ask a question to start one.</p>
          ) : (
            <ul className="min-h-0 space-y-1 overflow-y-auto">
              {items.map((c) => (
                <li key={c.id} className="group relative">
                  <button
                    type="button"
                    onClick={() => select(c.id)}
                    aria-current={c.id === selected ? "page" : undefined}
                    className={`block w-full rounded-lg px-3 py-2 pr-16 text-left transition-colors ${
                      c.id === selected
                        ? "bg-indigo-50 dark:bg-indigo-500/10"
                        : "hover:bg-zinc-100 dark:hover:bg-zinc-800/60"
                    }`}
                  >
                    <span className={`block truncate text-sm ${c.id === selected ? "font-medium text-indigo-700 dark:text-indigo-300" : "text-zinc-800 dark:text-zinc-200"}`}>
                      {c.title}
                    </span>
                    <span className="block text-xs text-zinc-400">{formatDateTime(c.updatedAt)}</span>
                  </button>
                  <div className="absolute top-1.5 right-1.5 flex opacity-100 md:opacity-0 md:group-focus-within:opacity-100 md:group-hover:opacity-100">
                    <Button variant="ghost" size="sm" aria-label={`Rename ${c.title}`} icon={<PencilIcon />} onClick={() => setDialog({ kind: "rename", conversation: c })} />
                    <Button variant="ghost" size="sm" aria-label={`Delete ${c.title}`} icon={<TrashIcon />} onClick={() => setDialog({ kind: "delete", conversation: c })} />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </aside>

        <section className={`min-h-0 flex-1 flex-col ${selected ? "flex" : "hidden md:flex"}`} aria-label="Messages">
          {selected && (
            <button type="button" onClick={() => select(null)} className="mb-2 inline-flex items-center gap-1 self-start text-sm text-zinc-500 md:hidden">
              <ChevronLeftIcon /> All chats
            </button>
          )}
          <ChatThread
            workspaceId={workspaceId}
            conversationId={selected}
            onCreated={(id) => select(id)}
            onActivity={conversations.reload}
          />
        </section>
      </div>

      {dialog?.kind === "rename" && (
        <RenameDialog
          conversation={dialog.conversation}
          onClose={() => setDialog(null)}
          onSave={async (title) => {
            await chatApi.rename(workspaceId, dialog.conversation.id, title);
            setDialog(null);
            conversations.reload();
          }}
        />
      )}
      {dialog?.kind === "delete" && (
        <ConfirmDialog
          title="Delete conversation?"
          message={
            <>
              <strong className="font-medium text-zinc-700 dark:text-zinc-200">{dialog.conversation.title}</strong> and all
              its messages and citations will be permanently deleted. Your documents are not affected.
            </>
          }
          confirmLabel="Delete conversation"
          onClose={() => setDialog(null)}
          onConfirm={async () => {
            await chatApi.remove(workspaceId, dialog.conversation.id);
            if (dialog.conversation.id === selected) select(null);
            setDialog(null);
            conversations.reload();
          }}
        />
      )}
    </div>
  );
}

function RenameDialog({
  conversation,
  onClose,
  onSave,
}: {
  conversation: ConversationSummary;
  onClose: () => void;
  onSave: (title: string) => Promise<void>;
}) {
  const [title, setTitle] = useState(conversation.title);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = title.trim();
    if (!value) return setError("Title is required");
    if (value.length > CHAT_LIMITS.title) return setError(`Title must be at most ${CHAT_LIMITS.title} characters`);
    setBusy(true);
    try {
      await onSave(value);
    } catch (e) {
      setError(toApiError(e).message);
      setBusy(false);
    }
  }

  return (
    <Modal
      title="Rename conversation"
      onClose={onClose}
      busy={busy}
      onSubmit={submit}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={busy}>Cancel</Button>
          <Button type="submit" loading={busy}>Save</Button>
        </>
      }
    >
      <TextField
        label="Title"
        value={title}
        onChange={(e) => {
          setTitle(e.target.value);
          setError(null);
        }}
        error={error ?? undefined}
        maxChars={CHAT_LIMITS.title}
        autoComplete="off"
      />
    </Modal>
  );
}
