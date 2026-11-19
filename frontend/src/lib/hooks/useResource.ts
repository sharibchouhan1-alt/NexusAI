"use client";

import { useCallback, useEffect, useState } from "react";
import { ApiError, toApiError } from "@/lib/api/client";

export type Loader<T> = (signal: AbortSignal) => Promise<T>;

type Settled<T> = { loader: Loader<T>; version: number } & (
  | { ok: true; data: T }
  | { ok: false; error: ApiError }
);

export interface Resource<T> {
  /**
   * Latest successful result for the current loader. Kept when a later reload fails, so a
   * transient error during background refresh doesn't blank the UI (check `error` too).
   */
  data: T | undefined;
  /** Error of the most recent attempt, if it failed. */
  error: ApiError | undefined;
  /** No result yet for the current loader (first load, or its inputs changed). */
  isLoading: boolean;
  /** Showing the previous result while a reload is in flight. */
  isRefreshing: boolean;
  reload: () => void;
}

/**
 * Loads data from the API on mount, whenever `loader` changes (memoize it with
 * useCallback), and on `reload()`. In-flight requests are aborted when superseded.
 * Results are tagged with the loader that produced them, so stale data from previous
 * inputs (e.g. another workspace) is never shown.
 */
export function useResource<T>(loader: Loader<T>): Resource<T> {
  const [version, setVersion] = useState(0);
  const [settled, setSettled] = useState<Settled<T> | null>(null);
  const [lastGood, setLastGood] = useState<{ loader: Loader<T>; data: T } | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    loader(controller.signal).then(
      (data) => {
        setSettled({ loader, version, ok: true, data });
        setLastGood({ loader, data });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) {
          setSettled({ loader, version, ok: false, error: toApiError(error) });
        }
      },
    );
    return () => controller.abort();
  }, [loader, version]);

  const reload = useCallback(() => setVersion((v) => v + 1), []);
  const current = settled?.loader === loader ? settled : null;
  const previous = lastGood?.loader === loader ? lastGood.data : undefined;

  return {
    data: current?.ok ? current.data : previous,
    error: current && !current.ok ? current.error : undefined,
    isLoading: current === null && previous === undefined,
    isRefreshing: current !== null && current.version !== version,
    reload,
  };
}
