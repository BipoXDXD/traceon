import { getJson } from '../api/http.ts';
import type { HttpResult } from '../api/http.ts';
import { parseHealthReport } from './healthContract.ts';
import type { HealthReport } from './healthContract.ts';

export const LIVENESS_PATH = '/health/live';
export const READINESS_PATH = '/health/ready';
export const REQUEST_TIMEOUT_MS = 5_000;

const DATABASE_CHECK_NAME = 'database';
const HTTP_OK = 200;
const HTTP_SERVICE_UNAVAILABLE = 503;
const FIRST_SERVER_ERROR = 500;

export type SystemStatus =
  | { readonly kind: 'operational'; readonly checkedAt: Date }
  | { readonly kind: 'database-unavailable'; readonly checkedAt: Date }
  | { readonly kind: 'api-unreachable'; readonly checkedAt: Date }
  | { readonly kind: 'unexpected-response'; readonly checkedAt: Date };

type CompletedResult = Exclude<HttpResult<HealthReport>, { kind: 'cancelled' }>;

export interface ProbeResults {
  readonly live: CompletedResult;
  readonly ready: CompletedResult;
  readonly checkedAt: Date;
}

/** Queries both probes in parallel; resolves `undefined` when the caller cancelled. */
export async function fetchSystemStatus(signal: AbortSignal): Promise<SystemStatus | undefined> {
  const [live, ready] = await Promise.all([
    getJson(LIVENESS_PATH, { parse: parseHealthReport, acceptedStatuses: [HTTP_OK], signal, timeoutMs: REQUEST_TIMEOUT_MS }),
    getJson(READINESS_PATH, {
      parse: parseHealthReport,
      acceptedStatuses: [HTTP_OK, HTTP_SERVICE_UNAVAILABLE],
      signal,
      timeoutMs: REQUEST_TIMEOUT_MS,
    }),
  ]);
  if (live.kind === 'cancelled' || ready.kind === 'cancelled') {
    return undefined;
  }
  return deriveSystemStatus({ live, ready, checkedAt: new Date() });
}

export function deriveSystemStatus({ live, ready, checkedAt }: ProbeResults): SystemStatus {
  if (isApiUnreachable(live)) {
    return { kind: 'api-unreachable', checkedAt };
  }
  if (!isLive(live)) {
    return { kind: 'unexpected-response', checkedAt };
  }
  return { kind: classifyReadiness(ready), checkedAt };
}

// Without a reachable API the Vite proxy answers 5xx itself, so a 5xx on liveness means "no API".
function isApiUnreachable(live: CompletedResult): boolean {
  return live.kind === 'unreachable' || (live.kind === 'unexpected-status' && live.status >= FIRST_SERVER_ERROR);
}

function isLive(live: CompletedResult): boolean {
  return live.kind === 'success' && live.data.status === 'Healthy';
}

function classifyReadiness(ready: CompletedResult): SystemStatus['kind'] {
  if (ready.kind !== 'success') {
    return 'unexpected-response';
  }
  const database = ready.data.checks.find((check) => check.name === DATABASE_CHECK_NAME);
  if (database === undefined) {
    return 'unexpected-response';
  }
  const consistent = ready.data.status === database.status;
  if (ready.status === HTTP_OK && consistent && database.status === 'Healthy') {
    return 'operational';
  }
  if (ready.status === HTTP_SERVICE_UNAVAILABLE && consistent && database.status === 'Unhealthy') {
    return 'database-unavailable';
  }
  return 'unexpected-response';
}
