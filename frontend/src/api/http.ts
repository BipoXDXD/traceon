// Single entry point for HTTP calls: every response body is treated as `unknown`
// and only becomes a typed value after the caller's parser accepts it.

export type HttpResult<T> =
  | { readonly kind: 'success'; readonly status: number; readonly data: T }
  | { readonly kind: 'unexpected-status'; readonly status: number }
  | { readonly kind: 'invalid-body'; readonly status: number }
  | { readonly kind: 'unreachable'; readonly reason: 'network' | 'timeout' }
  | { readonly kind: 'cancelled' };

export interface GetJsonOptions<T> {
  /** Returns the typed value, or `undefined` when the body breaks the contract. */
  readonly parse: (body: unknown) => T | undefined;
  /** Statuses whose body follows the contract; e.g. a readiness probe answers 503 with a valid body. */
  readonly acceptedStatuses: readonly number[];
  /** Cancellation by the caller (unmount, new check); reported as `cancelled`, not as a failure. */
  readonly signal: AbortSignal;
  readonly timeoutMs: number;
}

export async function getJson<T>(path: string, options: GetJsonOptions<T>): Promise<HttpResult<T>> {
  const { signal, timeoutMs } = options;
  if (signal.aborted) {
    return { kind: 'cancelled' };
  }

  const controller = new AbortController();
  const timer = setTimeout(() => {
    controller.abort();
  }, timeoutMs);
  const forwardCancellation = (): void => {
    controller.abort();
  };
  signal.addEventListener('abort', forwardCancellation, { once: true });

  try {
    const response = await fetch(path, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      signal: controller.signal,
    });
    return await readResponse(response, options);
  } catch {
    return describeFailure(signal, controller.signal);
  } finally {
    clearTimeout(timer);
    signal.removeEventListener('abort', forwardCancellation);
  }
}

// fetch only rejects on network failure or abort. The request signal is aborted either by the
// caller (forwarded) or by the timer, so "aborted but not by the caller" means timeout.
function describeFailure(callerSignal: AbortSignal, requestSignal: AbortSignal): HttpResult<never> {
  if (callerSignal.aborted) {
    return { kind: 'cancelled' };
  }
  return { kind: 'unreachable', reason: requestSignal.aborted ? 'timeout' : 'network' };
}

async function readResponse<T>(response: Response, options: GetJsonOptions<T>): Promise<HttpResult<T>> {
  const { status } = response;
  if (!options.acceptedStatuses.includes(status)) {
    return { kind: 'unexpected-status', status };
  }
  const data = options.parse(parseJson(await response.text()));
  return data === undefined ? { kind: 'invalid-body', status } : { kind: 'success', status, data };
}

function parseJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    // Not JSON (e.g. an HTML error page): the parser receives undefined and rejects it.
    return undefined;
  }
}
