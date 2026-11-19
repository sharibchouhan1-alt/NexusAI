"use client";

import { useCallback, useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import { toApiError } from "@/lib/api/client";
import { chatApi } from "@/lib/api/chat";
import { CHAT_LIMITS, type ChatMessage, type Citation, type ConversationDetail, type Uuid } from "@/lib/api/types";
import { useResource } from "@/lib/hooks/useResource";
import { Button } from "@/components/ui/Button";
import { ChatIcon, SendIcon } from "@/components/ui/icons";
import { Alert, ErrorState, LoadingState } from "@/components/ui/states";
import { MessageItem } from "./MessageItem";
import { SourceDialog } from "./SourceDialog";

/** While the server reports an answer as PENDING (e.g. asked from another tab), refresh at this pace. */
const PENDING_POLL_MS = 3000;

interface Optimistic {
  conversationId: Uuid | null;
  question: string;
  since: number;
}

interface ChatThreadProps {
  workspaceId: Uuid;
  conversationId: Uuid | null;
  /** A new conversation was created by the first question. */
  onCreated: (conversationId: Uuid) => void;
  /** Titles or ordering may have changed (refresh the conversation list). */
  onActivity: () => void;
}

export function ChatThread({ workspaceId, conversationId, onCreated, onActivity }: ChatThreadProps) {
  const [draft, setDraft] = useState("");
  const [optimistic, setOptimistic] = useState<Optimistic | null>(null);
  const [sendError, setSendError] = useState<string | null>(null);
  const [retryingId, setRetryingId] = useState<Uuid | null>(null);
  const [source, setSource] = useState<Citation | null>(null);
  const endRef = useRef<HTMLDivElement>(null);

  const loader = useCallback(
    (signal: AbortSignal): Promise<ConversationDetail | null> =>
      conversationId ? chatApi.get(workspaceId, conversationId, signal) : Promise.resolve(null),
    [workspaceId, conversationId],
  );
  const conversation = useResource(loader);
  const { reload } = conversation;
  const messages: ChatMessage[] = conversation.data?.messages ?? [];

  const serverPending = messages.some((m) => m.status === "PENDING");
  const busy = optimistic !== null || serverPending || retryingId !== null;

  useEffect(() => {
    if (!serverPending || optimistic) return;
    const timer = setTimeout(reload, PENDING_POLL_MS);
    return () => clearTimeout(timer);
  }, [serverPending, optimistic, conversation.data, reload]);

  // The in-flight turn stays visible while a brand-new conversation's id propagates to the URL.
  const optimisticVisible =
    optimistic !== null && (optimistic.conversationId === conversationId || conversationId === null || optimistic.conversationId === null);
  const shown: ChatMessage[] = [...messages];
  if (optimistic && optimisticVisible) {
    const now = new Date(optimistic.since).toISOString();
    const placeholder = { conversationId: conversationId ?? "", statusDetail: null, model: null, retrievedCount: null,
      latencyMs: null, createdAt: now, citations: [] };
    shown.push(
      { ...placeholder, id: "pending-question", role: "USER", content: optimistic.question, status: "COMPLETE" },
      { ...placeholder, id: "pending-answer", role: "ASSISTANT", content: "", status: "PENDING" },
    );
  }

  const count = shown.length;
  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [count, conversation.data]);

  async function send(event?: FormEvent) {
    event?.preventDefault();
    const question = draft.trim();
    if (!question || busy) return;
    if (question.length > CHAT_LIMITS.question) {
      setSendError(`Questions are limited to ${CHAT_LIMITS.question} characters.`);
      return;
    }
    setSendError(null);
    setDraft("");
    let targetId = conversationId;
    setOptimistic({ conversationId: targetId, question, since: Date.now() });
    try {
      if (!targetId) {
        targetId = (await chatApi.create(workspaceId)).id;
        setOptimistic({ conversationId: targetId, question, since: Date.now() });
        onCreated(targetId);
      }
      await chatApi.ask(workspaceId, targetId, question);
    } catch (error) {
      const apiError = toApiError(error);
      setSendError(
        apiError.status === 409
          ? "The previous question is still being answered. Wait for it to finish, then send again."
          : apiError.isNetworkError
            ? `${apiError.message} Your question may not have been saved.`
            : apiError.message,
      );
      if (apiError.status !== 201) setDraft(question); // let the user resend
    } finally {
      setOptimistic(null);
      reload();
      onActivity();
    }
  }

  async function retry(messageId: Uuid) {
    if (!conversationId) return;
    setRetryingId(messageId);
    setSendError(null);
    try {
      await chatApi.retry(workspaceId, conversationId, messageId);
    } catch (error) {
      setSendError(toApiError(error).message);
    } finally {
      setRetryingId(null);
      reload();
      onActivity();
    }
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      void send();
    }
  }

  const lastMessage = messages.at(-1);

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="min-h-0 flex-1 overflow-y-auto px-1 pb-4">
        {conversation.isLoading && conversationId && !optimisticVisible ? (
          <LoadingState label="Loading conversation…" />
        ) : conversation.error && !conversation.data ? (
          <ErrorState
            title={conversation.error.isNotFound ? "Conversation not found" : "Couldn't load this conversation"}
            error={conversation.error}
            onRetry={conversation.error.isNotFound ? undefined : reload}
          />
        ) : shown.length === 0 ? (
          <div className="flex h-full flex-col items-center justify-center px-6 py-16 text-center">
            <span className="mb-4 flex size-12 items-center justify-center rounded-full bg-indigo-50 text-indigo-600 dark:bg-indigo-500/10 dark:text-indigo-400">
              <ChatIcon className="size-5" />
            </span>
            <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">Ask about this workspace&apos;s documents</h2>
            <p className="mt-1 max-w-md text-sm text-zinc-500 dark:text-zinc-400">
              Answers come only from your indexed documents and cite the exact passages they rely on. If the documents
              don&apos;t support an answer, NexusAI says so instead of guessing. Everything runs locally.
            </p>
          </div>
        ) : (
          <ol className="space-y-4" aria-label="Conversation">
            {shown.map((message) => (
              <MessageItem
                key={message.id}
                message={message}
                pendingSince={message.id === "pending-answer" ? optimistic?.since : undefined}
                canRetry={message.status === "FAILED" && message.id === lastMessage?.id && !busy}
                retrying={retryingId === message.id}
                onRetry={() => retry(message.id)}
                onCite={setSource}
              />
            ))}
          </ol>
        )}
        <div ref={endRef} />
      </div>

      <form onSubmit={send} className="border-t border-zinc-200 pt-3 dark:border-zinc-800">
        {sendError && (
          <div className="mb-3">
            <Alert>{sendError}</Alert>
          </div>
        )}
        <div className="flex items-end gap-2">
          <textarea
            value={draft}
            onChange={(e) => {
              setDraft(e.target.value);
              setSendError(null);
            }}
            onKeyDown={onKeyDown}
            rows={2}
            maxLength={CHAT_LIMITS.question + 1}
            placeholder={busy ? "Waiting for the current answer…" : "Ask a question about your documents… (Enter to send, Shift+Enter for a new line)"}
            aria-label="Your question"
            disabled={conversation.error?.isNotFound}
            className="block max-h-48 min-h-11 flex-1 resize-y rounded-lg border-0 bg-white px-3 py-2.5 text-sm text-zinc-900 shadow-sm ring-1 ring-zinc-300 ring-inset placeholder:text-zinc-400 focus:ring-2 focus:ring-indigo-600 focus:outline-none dark:bg-zinc-900 dark:text-zinc-100 dark:ring-zinc-700"
          />
          <Button type="submit" disabled={busy || !draft.trim()} loading={optimistic !== null} icon={optimistic ? undefined : <SendIcon />} className="h-11">
            Send
          </Button>
        </div>
        <p className="mt-1.5 text-[11px] text-zinc-400">
          Answers are generated by a local model and grounded in this workspace only. Local generation can take 10–60 seconds.
        </p>
      </form>

      {source && <SourceDialog workspaceId={workspaceId} citation={source} onClose={() => setSource(null)} />}
    </div>
  );
}
