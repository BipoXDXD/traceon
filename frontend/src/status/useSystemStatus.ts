import { useCallback, useEffect, useState } from 'react';
import { fetchSystemStatus } from './systemStatus.ts';
import type { SystemStatus } from './systemStatus.ts';

export type SystemStatusState =
  | { readonly kind: 'checking'; readonly lastCheckedAt: Date | undefined }
  | SystemStatus;

export interface UseSystemStatus {
  readonly state: SystemStatusState;
  readonly recheck: () => void;
}

export function useSystemStatus(): UseSystemStatus {
  const [state, setState] = useState<SystemStatusState>({ kind: 'checking', lastCheckedAt: undefined });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    // Cleanup aborts the in-flight check on unmount and when a new attempt starts,
    // so a slow, stale answer can never overwrite a newer one.
    const controller = new AbortController();
    void fetchSystemStatus(controller.signal).then((status) => {
      // fetch rejects on abort, but a body that was already arriving may still resolve: check again.
      if (status !== undefined && !controller.signal.aborted) {
        setState(status);
      }
    });
    return () => {
      controller.abort();
    };
  }, [attempt]);

  const recheck = useCallback(() => {
    setState((previous) => ({
      kind: 'checking',
      lastCheckedAt: previous.kind === 'checking' ? previous.lastCheckedAt : previous.checkedAt,
    }));
    setAttempt((current) => current + 1);
  }, []);

  return { state, recheck };
}
