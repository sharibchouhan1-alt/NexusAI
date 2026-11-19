import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/ui/states";
import { WorkspaceDetail } from "@/components/workspaces/WorkspaceDetail";

export const metadata: Metadata = { title: "Workspace" };

export default function WorkspacePage({ params }: PageProps<"/workspaces/[workspaceId]">) {
  // params resolve per request; the Suspense boundary keeps the shell static.
  return (
    <Suspense fallback={<LoadingState label="Loading workspace…" />}>
      <WorkspaceDetail params={params} />
    </Suspense>
  );
}
