"use client";

import { createContext, useCallback, useContext, type ReactNode } from "react";
import { workspacesApi } from "@/lib/api/workspaces";
import { LIMITS, type Page, type Workspace } from "@/lib/api/types";
import { useResource, type Resource } from "@/lib/hooks/useResource";

/** The workspace list shown in navigation, shared so any page can refresh it after a change. */
const WorkspaceNavContext = createContext<Resource<Page<Workspace>> | null>(null);

export function WorkspaceNavProvider({ children }: { children: ReactNode }) {
  const loader = useCallback(
    (signal: AbortSignal) => workspacesApi.list(0, LIMITS.pageSizeMax, signal),
    [],
  );
  const resource = useResource(loader);
  return <WorkspaceNavContext.Provider value={resource}>{children}</WorkspaceNavContext.Provider>;
}

export function useWorkspaceNav() {
  const value = useContext(WorkspaceNavContext);
  if (!value) throw new Error("useWorkspaceNav must be used inside WorkspaceNavProvider");
  return value;
}
