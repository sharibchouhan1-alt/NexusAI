"use client";

import { useState, type FormEvent } from "react";
import { toApiError } from "@/lib/api/client";
import { researchApi } from "@/lib/api/research";
import { RESEARCH_LIMITS, type Uuid } from "@/lib/api/types";
import { Button } from "@/components/ui/Button";
import { SearchIcon } from "@/components/ui/icons";
import { Alert } from "@/components/ui/states";

/** Starts a research run. The run executes in the background; the page then follows its progress. */
export function NewResearchForm({ workspaceId, onStarted }: { workspaceId: Uuid; onStarted: (runId: Uuid) => void }) {
  const [objective, setObjective] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const text = objective.trim();
    if (text.length < RESEARCH_LIMITS.objectiveMin) {
      setError(`Describe the research objective in at least ${RESEARCH_LIMITS.objectiveMin} characters.`);
      return;
    }
    if (text.length > RESEARCH_LIMITS.objectiveMax) {
      setError(`Objectives are limited to ${RESEARCH_LIMITS.objectiveMax} characters.`);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const run = await researchApi.start(workspaceId, text);
      setObjective("");
      onStarted(run.id);
    } catch (e) {
      setError(toApiError(e).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate className="rounded-xl border border-zinc-200 bg-white p-5 dark:border-zinc-800 dark:bg-zinc-900">
      <h2 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">New research</h2>
      <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
        The agent plans the research, searches and reads this workspace&apos;s documents with its tools, and writes a
        report in which every claim cites a verified passage. It runs locally and can take a few minutes.
      </p>
      <label htmlFor="objective" className="mt-4 mb-1.5 block text-sm font-medium text-zinc-800 dark:text-zinc-200">
        Research objective
      </label>
      <textarea
        id="objective"
        value={objective}
        onChange={(e) => {
          setObjective(e.target.value);
          setError(null);
        }}
        rows={3}
        maxLength={RESEARCH_LIMITS.objectiveMax + 1}
        placeholder="e.g. Why was the product launch delayed, and what happens if it slips again?"
        aria-invalid={error ? true : undefined}
        className="block w-full resize-y rounded-lg border-0 bg-white px-3 py-2.5 text-sm text-zinc-900 shadow-sm ring-1 ring-zinc-300 ring-inset placeholder:text-zinc-400 focus:ring-2 focus:ring-indigo-600 focus:outline-none dark:bg-zinc-950 dark:text-zinc-100 dark:ring-zinc-700"
      />
      {error && (
        <div className="mt-3">
          <Alert>{error}</Alert>
        </div>
      )}
      <div className="mt-4 flex items-center justify-between gap-3">
        <span className="text-xs text-zinc-400 tabular-nums">
          {objective.trim().length}/{RESEARCH_LIMITS.objectiveMax}
        </span>
        <Button type="submit" loading={busy} icon={<SearchIcon />}>
          Start research
        </Button>
      </div>
    </form>
  );
}
