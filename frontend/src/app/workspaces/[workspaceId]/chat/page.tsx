import type { Metadata } from "next";
import { Suspense } from "react";
import { ChatPage } from "@/components/chat/ChatPage";
import { LoadingState } from "@/components/ui/states";

export const metadata: Metadata = { title: "Chat" };

export default function WorkspaceChatPage({ params }: PageProps<"/workspaces/[workspaceId]/chat">) {
  // params and the ?c= conversation id are request-specific: resolved inside Suspense.
  return (
    <Suspense fallback={<LoadingState label="Loading chat…" />}>
      <ChatPage params={params} />
    </Suspense>
  );
}
