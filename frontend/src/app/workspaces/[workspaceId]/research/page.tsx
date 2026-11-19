import type { Metadata } from "next";
import { Suspense } from "react";
import { ResearchPage } from "@/components/research/ResearchPage";
import { LoadingState } from "@/components/ui/states";

export const metadata: Metadata = { title: "Research" };

export default function WorkspaceResearchPage({ params }: PageProps<"/workspaces/[workspaceId]/research">) {
  // params and the ?r= run id are request-specific: resolved inside Suspense.
  return (
    <Suspense fallback={<LoadingState label="Loading research…" />}>
      <ResearchPage params={params} />
    </Suspense>
  );
}
